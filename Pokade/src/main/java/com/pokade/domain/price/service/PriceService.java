package com.pokade.domain.price.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardPrice;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.price.ChartPeriod;
import com.pokade.domain.price.RankingType;
import com.pokade.domain.price.StatsPeriod;
import com.pokade.domain.price.dto.BuyOfferFulfillRequest;
import com.pokade.domain.price.dto.BuyOfferOrderbookEntryResponse;
import com.pokade.domain.price.dto.BuyOfferPaymentConfirmRequest;
import com.pokade.domain.price.dto.BuyOfferReadyRequest;
import com.pokade.domain.price.dto.BuyOfferReadyResponse;
import com.pokade.domain.price.dto.BuyOfferResponse;
import com.pokade.domain.price.dto.CardPricePointResponse;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.entity.BuyOffer;
import com.pokade.domain.price.entity.BuyOfferOrder;
import com.pokade.domain.price.entity.BuyOfferOrderStatus;
import com.pokade.domain.price.repository.BuyOfferOrderRepository;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.price.repository.PriceCardStatsRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceService {

    private static final String CURRENCY = "KRW";
    private static final int RECENT_TRADES_LIMIT = 20;
    private static final int MAX_SUMMARIES_BATCH_SIZE = 100;
    private static final int STATS_PERIOD_DAYS = 7;
    private static final ListingGrade STATS_GRADE = ListingGrade.S;
    private static final int RANKING_LIMIT = 10;
    // CardUpsertService가 Scrydex 동기화 시 raw NM 시세를 저장하는 키와 동일해야 한다(card_prices 조회 fallback용).
    private static final String RAW_PRICE_TYPE = "raw";
    private static final String RAW_GRADE = "";
    private static final String RAW_COMPANY = "";
    // 구매입찰 결제 시 상품가에 더하는 고정 배송비(KRW) - TradeService.ready()의 즉시구매용 배송비와
    // 동일한 값/성격이며, 두 도메인이 각자 자기 상수로 갖는다(공유 상수 모듈은 아직 없음).
    private static final int SHIPPING_FEE = 3000;

    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final ListingRepository listingRepository;
    private final BuyOfferRepository buyOfferRepository;
    private final BuyOfferOrderRepository buyOfferOrderRepository;
    private final TradeRepository tradeRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;
    private final PriceCardStatsRepository priceCardStatsRepository;
    private final CardPriceRepository cardPriceRepository;
    private final UserAccessChecker userAccessChecker;
    private final UserRepository userRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PointService pointService;
    // 즉시판매(구매입찰 체결) 시 실제 Trade/Payment 생성만 위임한다 - 이번 기능 한정으로 domain.trade
    // 수정을 허락받고 진행(TradeService.createMatchedTrade 참고).
    private final TradeService tradeService;
    // 임시 계측 - Grafana 테스트용, 팀 논의 전 커밋 대상 아님
    private final MeterRegistry meterRegistry;

    public PriceSummaryResponse getSummary(Long cardId, Long variantId) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        Integer buyPrice = listingRepository
                .findLowestActivePrice(cardId, resolvedVariantId, ListingStatus.ACTIVE)
                .orElse(null);
        Integer sellPrice = buyOfferRepository.findHighestActivePrice(cardId, resolvedVariantId).orElse(null);

        return new PriceSummaryResponse(buyPrice, sellPrice, CURRENCY);
    }

    // N+1을 피하기 위한 배치 버전.
    public List<CardPriceSummaryResponse> getSummaries(List<Long> cardIds, ListingGrade grade,
                                                         boolean includeRecentTradePrice) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "cardIds는 최소 1개 이상 필요합니다.");
        }
        if (cardIds.size() > MAX_SUMMARIES_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "cardIds는 최대 " + MAX_SUMMARIES_BATCH_SIZE + "개까지 조회할 수 있습니다.");
        }
        // 임시 계측 - Grafana 테스트용, 팀 논의 전 커밋 대상 아님 (배치 API가 실제로 몇 개씩 묶여 호출되는지 확인)
        meterRegistry.summary("price.summaries.batch_size").record(cardIds.size());

        List<Long> distinctCardIds = cardIds.stream().distinct().toList();

        Map<Long, Long> primaryVariantByCard = cardVariantRepository
                .findPrimaryVariantIdsByCardIds(distinctCardIds).stream()
                .collect(Collectors.toMap(
                        CardVariantRepository.PrimaryVariantIdView::getCardId,
                        CardVariantRepository.PrimaryVariantIdView::getVariantId));

        List<Long> variantIds = primaryVariantByCard.values().stream().distinct().toList();

        Map<Long, Integer> buyPriceByVariant = variantIds.isEmpty()
                ? Map.of()
                : listingRepository.findLowestActivePricesByVariantIds(variantIds, ListingStatus.ACTIVE, grade).stream()
                        .collect(Collectors.toMap(
                                ListingRepository.VariantPriceView::getVariantId,
                                ListingRepository.VariantPriceView::getPrice));

        Map<Long, Integer> sellPriceByVariant = variantIds.isEmpty()
                ? Map.of()
                : buyOfferRepository.findHighestActivePricesByVariantIds(variantIds).stream()
                        .collect(Collectors.toMap(
                                BuyOfferRepository.VariantPriceView::getVariantId,
                                BuyOfferRepository.VariantPriceView::getPrice));

        // 참고용 표시값 - buyPrice의 매물 유무 신호는 그대로 유지하며, 요청 시에만 조회한다.
        Map<Long, Integer> recentTradePriceByCard = !includeRecentTradePrice
                ? Map.of()
                : priceTradeStatsRepository
                        .findRecentCompletedTradePricesByCardIds(distinctCardIds, grade, TradeStatus.COMPLETED).stream()
                        .collect(Collectors.toMap(
                                PriceTradeStatsRepository.CardPriceView::getCardId,
                                PriceTradeStatsRepository.CardPriceView::getPrice));

        // buyPrice/recentTradePrice가 둘 다 없는 카드용 fallback - Scrydex 동기화 비등급(raw) 시세.
        // 우리 플랫폼 거래 이력이 없는 대다수 카드(직접 거래된 적 없는 카드)도 "가격 정보 없음" 대신 참고 시세를 보여주기 위함.
        Map<Long, CardPriceRepository.VariantMarketPriceView> marketPriceByVariant = variantIds.isEmpty()
                ? Map.of()
                : cardPriceRepository
                        .findMarketPricesByVariantIds(variantIds, RAW_PRICE_TYPE, RAW_GRADE, RAW_COMPANY).stream()
                        .collect(Collectors.toMap(
                                CardPriceRepository.VariantMarketPriceView::getVariantId,
                                view -> view));

        return distinctCardIds.stream()
                .map(cardId -> {
                    Long variantId = primaryVariantByCard.get(cardId);
                    Integer buyPrice = variantId != null ? buyPriceByVariant.get(variantId) : null;
                    Integer sellPrice = variantId != null ? sellPriceByVariant.get(variantId) : null;
                    Integer recentTradePrice = recentTradePriceByCard.get(cardId);
                    CardPriceRepository.VariantMarketPriceView marketPrice =
                            variantId != null ? marketPriceByVariant.get(variantId) : null;
                    return new CardPriceSummaryResponse(cardId, buyPrice, sellPrice, recentTradePrice, CURRENCY,
                            marketPrice != null ? marketPrice.getMarket() : null,
                            marketPrice != null ? marketPrice.getCurrency() : null);
                })
                .toList();
    }

    // 구매입찰 호가창 - domain.listing.getOrderbook()(매도)과 대응되는 매수 호가창 조회.
    // BuyOffer는 domain.price가 소유한 엔티티라 이 서비스에 둔다.
    public List<BuyOfferOrderbookEntryResponse> getBuyOfferOrderbook(Long cardId, Long variantId, ListingGrade grade) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        return buyOfferRepository
                .findOrderbook(cardId, resolvedVariantId, grade)
                .stream()
                .map(BuyOfferOrderbookEntryResponse::of)
                .toList();
    }

    // 구매입찰 등록 결제 준비 - 등록과 동시에 토스 에스크로 결제를 진행하므로(사용자 결정), 매물
    // 즉시구매의 TradeService.ready()와 달리 "이미 있는 리소스를 잠그는" 개념이 없다 - 그냥 새 PENDING
    // 주문을 기록하고, 결제가 실제로 승인된 뒤 confirmBuyOfferPurchase()에서 BuyOffer를 생성한다.
    @Transactional
    public BuyOfferReadyResponse readyBuyOffer(Long buyerId, BuyOfferReadyRequest request) {
        userAccessChecker.assertWritable(buyerId);

        if (!cardRepository.existsById(request.cardId())) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        Long resolvedVariantId = request.variantId() != null
                ? request.variantId()
                : cardVariantRepository.findPrimaryVariantId(request.cardId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        int totalAmount = request.price() + SHIPPING_FEE;
        int pointsToUse = request.pointsToUse();
        if (pointsToUse > totalAmount) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "포인트 사용액이 결제 금액보다 클 수 없습니다.");
        }
        if (pointsToUse > 0) {
            // 실제 차감은 결제 승인 시점(confirmBuyOfferPurchase)에서 한다 - 여기서는 등록을 포기하거나
            // 결제를 완료하지 않아도 포인트가 미리 묶이지 않도록, 잔액이 충분한지만 미리 확인해 준다.
            User buyer = userRepository.findById(buyerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (buyer.getPointBalance() < pointsToUse) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_POINT_BALANCE);
            }
        }

        String orderId = UUID.randomUUID().toString();
        int paymentAmount = totalAmount - pointsToUse;

        buyOfferOrderRepository.save(BuyOfferOrder.builder()
                .orderId(orderId)
                .buyerId(buyerId)
                .cardId(request.cardId())
                .variantId(resolvedVariantId)
                .grade(request.grade())
                .price(request.price())
                .shippingFee(SHIPPING_FEE)
                .pointsUsed(pointsToUse)
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .recipientAddress(request.recipientAddress())
                .build());

        return new BuyOfferReadyResponse(orderId, paymentAmount);
    }

    // 결제 승인 콜백 처리 - TradeService.confirmPurchase()와 동일한 뼈대(주문 조회 → 소유자/상태/금액
    // 검증 → 토스 승인 → 실패 시 REQUIRES_NEW로 FAILED 기록)이지만, 구매입찰은 잠글 기존 리소스가
    // 없으므로 승인 후 경쟁 상태 재검증(markAsTrading 상당)이나 승인-후-취소 분기가 없다.
    // 포인트 사용액이 있으면 토스 승인보다 먼저 차감한다 - 이후 토스 승인이 실패해 예외가 다시
    // 던져지면 이 메서드의 트랜잭션 전체가 롤백되므로(pointService.use()도 같은 트랜잭션에 참여),
    // 별도 환불 호출 없이도 방금 차감한 포인트가 함께 되돌아간다.
    @Transactional
    public BuyOfferResponse confirmBuyOfferPurchase(Long buyerId, String paymentKey, String orderId, long amount) {
        BuyOfferOrder order = buyOfferOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUY_OFFER_ORDER_NOT_FOUND));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (order.getStatus() != BuyOfferOrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUY_OFFER_ORDER_ALREADY_PROCESSED);
        }
        if (order.getPaymentAmount() != amount) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 금액이 일치하지 않습니다.");
        }
        if (order.getPaymentAmount() > 0 && (paymentKey == null || paymentKey.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "paymentKey는 필수입니다.");
        }

        try {
            if (order.getPointsUsed() > 0) {
                pointService.use(buyerId, order.getPointsUsed(), null);
            }
            if (order.getPaymentAmount() > 0) {
                tossPaymentClient.confirmPayment(paymentKey, orderId, order.getPaymentAmount());
            }
        } catch (BusinessException e) {
            buyOfferOrderRepository.markFailedIfPending(orderId);
            throw e;
        }

        BuyOffer buyOffer = BuyOffer.builder()
                .cardId(order.getCardId())
                .buyerId(buyerId)
                .variantId(order.getVariantId())
                .price(order.getPrice())
                .grade(order.getGrade())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .recipientAddress(order.getRecipientAddress())
                .tossPaymentKey(order.getPaymentAmount() > 0 ? paymentKey : null)
                .pointsUsed(order.getPointsUsed())
                .shippingFee(order.getShippingFee())
                .build();
        BuyOffer saved = buyOfferRepository.save(buyOffer);

        order.markConfirmed();

        return BuyOfferResponse.of(saved);
    }

    // 즉시판매 - 이미 결제(토스 에스크로 또는 포인트)가 끝난 구매입찰에 판매자가 자기 카드를 바로
    // 매칭시킨다. 매물을 먼저 등록하는 절차 없이, 이 매칭 전용으로 매물을 만들어 즉시 TRADING으로
    // 잠근 뒤(같은 트랜잭션 안이라 다른 사용자에게 ACTIVE로 노출될 일이 없다) 거래를 생성한다.
    @Transactional
    public TradeResponse fulfillBuyOffer(Long buyOfferId, Long sellerId, BuyOfferFulfillRequest request) {
        BuyOffer buyOffer = buyOfferRepository.findById(buyOfferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUY_OFFER_NOT_FOUND));

        if (buyOffer.getBuyerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.SELF_BUY_OFFER_NOT_ALLOWED);
        }
        // TradeService.ready()와 동일하게 양쪽(구매자/판매자) 모두 정지/탈퇴 여부를 검증한다 -
        // 구매입찰 등록 이후 구매자 계정이 정지됐을 수도 있기 때문.
        userAccessChecker.assertWritable(sellerId);
        userAccessChecker.assertWritable(buyOffer.getBuyerId());
        // ACTIVE/만료 여부 검증 및 상태 전이를 매물 생성보다 먼저 한다 - 이미 체결된 입찰이면
        // 매물을 만들 필요 없이 여기서 바로 실패한다.
        buyOffer.markMatched();

        Listing listing = listingRepository.save(
                Listing.builder()
                        .cardId(buyOffer.getCardId())
                        .sellerId(sellerId)
                        .variantId(buyOffer.getVariantId())
                        .price(buyOffer.getPrice())
                        .grade(buyOffer.getGrade())
                        .settlementBankName(request.settlementBankName())
                        .settlementAccountNumber(request.settlementAccountNumber())
                        .settlementAccountHolder(request.settlementAccountHolder())
                        .returnRecipientName(request.returnRecipientName())
                        .returnRecipientPhone(request.returnRecipientPhone())
                        .returnAddress(request.returnAddress())
                        .build()
        );

        // Payment.amount는 판매자 정산 기준가(product price)가 아니라 실제로 결제된 금액
        // (상품가+배송비-포인트사용액) - TradeService.confirmPurchase()가 지키는 것과 동일한
        // 규칙(그 메서드의 주석 참고). Trade.price(=buyOffer.getPrice())는 그대로 상품가만 유지한다.
        int paymentAmount = buyOffer.getPrice() + buyOffer.getShippingFee() - buyOffer.getPointsUsed();

        return tradeService.createMatchedTrade(
                listing.getId(),
                buyOffer.getBuyerId(),
                buyOffer.getPrice(),
                paymentAmount,
                buyOffer.getRecipientName(),
                buyOffer.getRecipientPhone(),
                buyOffer.getRecipientAddress(),
                buyOffer.getTossPaymentKey(),
                buyOffer.getPointsUsed()
        );
    }

    public List<TradeSummaryResponse> getRecentTrades(Long cardId) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        return tradeRepository
                .findRecentCompletedTrades(cardId, TradeStatus.COMPLETED, PageRequest.of(0, RECENT_TRADES_LIMIT))
                .stream()
                .map(TradeSummaryResponse::of)
                .toList();
    }

    public List<TradeSummaryResponse> getPriceChart(Long cardId, String period) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }

        ChartPeriod chartPeriod = ChartPeriod.from(period);
        // 임시 계측 - Grafana 테스트용, 팀 논의 전 커밋 대상 아님
        meterRegistry.counter("price.chart.requests", "period", chartPeriod.name()).increment();
        LocalDateTime from = LocalDateTime.now().minusDays(chartPeriod.getDays());

        return tradeRepository
                .findCompletedTradesSince(cardId, TradeStatus.COMPLETED, from)
                .stream()
                .map(TradeSummaryResponse::of)
                .toList();
    }

    // FR-PRICE-04: 카드 상세용 시세 등락률/거래량.
    // grade/period를 지정하지 않으면 기존과 동일하게 자체 AI등급(S) COMPLETED 거래 이력의 7일 블록 비교로 계산한다
    // (프론트가 대표 시세로 S등급을 쓰는 것과 일관되게 맞춘 원래 설계, 하위 호환 유지).
    // grade/period를 지정하면 card_prices(Scrydex 동기화 + PSA10/PSA9 외 S/A/B 목업)의 change_*_pct 컬럼을 직접 조회한다.
    public PriceStatsResponse getStats(Long cardId, Long variantId, ListingGrade grade, String period) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }
        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        if (grade == null && period == null) {
            return getStatsFromTrades(cardId);
        }

        StatsPeriod statsPeriod = StatsPeriod.from(period != null ? period : StatsPeriod.DAYS_7.getCode());
        return getStatsFromCardPrices(resolvedVariantId, grade != null ? grade : STATS_GRADE, statsPeriod);
    }

    private PriceStatsResponse getStatsFromTrades(Long cardId) {
        LocalDateTime recentFrom = LocalDateTime.now().minusDays(STATS_PERIOD_DAYS);
        LocalDateTime previousFrom = LocalDateTime.now().minusDays(STATS_PERIOD_DAYS * 2L);

        long volume = priceTradeStatsRepository.countCompletedTradesByGradeSince(
                cardId, STATS_GRADE, TradeStatus.COMPLETED, recentFrom);

        Double recentAvg = priceTradeStatsRepository.findAveragePriceByGradeSince(
                cardId, STATS_GRADE, TradeStatus.COMPLETED, recentFrom);
        Double previousAvg = priceTradeStatsRepository.findAveragePriceByGradeBetween(
                cardId, STATS_GRADE, TradeStatus.COMPLETED, previousFrom, recentFrom);

        if (recentAvg == null || previousAvg == null) {
            return new PriceStatsResponse(BigDecimal.ZERO, 0L, volume);
        }

        BigDecimal recentAvgAmount = BigDecimal.valueOf(recentAvg);
        BigDecimal previousAvgAmount = BigDecimal.valueOf(previousAvg);
        BigDecimal diff = recentAvgAmount.subtract(previousAvgAmount);
        BigDecimal changeRate = diff
                .divide(previousAvgAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        long changeAmount = diff.setScale(0, RoundingMode.HALF_UP).longValue();

        return new PriceStatsResponse(changeRate, changeAmount, volume);
    }
    
    public Map<Long, BigDecimal> getChangeRates(List<Long> cardIds) {
        LocalDateTime recentFrom = LocalDateTime.now().minusDays(STATS_PERIOD_DAYS);
        LocalDateTime previousFrom = LocalDateTime.now().minusDays(STATS_PERIOD_DAYS * 2L);

        Map<Long, Double> recentAvgByCard = priceTradeStatsRepository
                .findAveragePricesByGradeSince(STATS_GRADE, TradeStatus.COMPLETED, recentFrom).stream()
                .collect(Collectors.toMap(
                        PriceTradeStatsRepository.CardAvgPriceView::getCardId,
                        PriceTradeStatsRepository.CardAvgPriceView::getAvgPrice));
        Map<Long, Double> previousAvgByCard = priceTradeStatsRepository
                .findAveragePricesByGradeBetween(STATS_GRADE, TradeStatus.COMPLETED, previousFrom, recentFrom).stream()
                .collect(Collectors.toMap(
                        PriceTradeStatsRepository.CardAvgPriceView::getCardId,
                        PriceTradeStatsRepository.CardAvgPriceView::getAvgPrice));

        return cardIds.stream().distinct().collect(Collectors.toMap(
                Function.identity(),
                cardId -> computeChangeRate(recentAvgByCard.get(cardId), previousAvgByCard.get(cardId))));
    }

    private BigDecimal computeChangeRate(Double recentAvg, Double previousAvg) {
        if (recentAvg == null || previousAvg == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal recentAvgAmount = BigDecimal.valueOf(recentAvg);
        BigDecimal previousAvgAmount = BigDecimal.valueOf(previousAvg);
        return recentAvgAmount.subtract(previousAvgAmount)
                .divide(previousAvgAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // PSA10/PSA9/PSA8은 공인 등급이라 company='PSA', 자체 AI등급(S/A/B)은 company=''.
    private PriceStatsResponse getStatsFromCardPrices(Long variantId, ListingGrade grade, StatsPeriod period) {
        GradeKey gradeKey = toGradeKey(grade);

        return priceCardStatsRepository
                .findChangeByVariantGradeCompanyAndPeriod(variantId, gradeKey.grade(), gradeKey.company(), period.getCode())
                .map(view -> {
                    BigDecimal changeRate = view.getChangePct() != null ? view.getChangePct() : BigDecimal.ZERO;
                    // change_7d_amount 컬럼만 존재해서 7일 외 기간은 금액을 못 구한다 - null로 남긴다(0으로 채우면 "변동 없음"처럼 보여 오해를 줌).
                    Long changeAmount = period == StatsPeriod.DAYS_7 && view.getChange7dAmount() != null
                            ? view.getChange7dAmount().setScale(0, RoundingMode.HALF_UP).longValue()
                            : null;
                    return new PriceStatsResponse(changeRate, changeAmount, 0L);
                })
                .orElseGet(() -> new PriceStatsResponse(BigDecimal.ZERO, null, 0L));
    }

    private GradeKey toGradeKey(ListingGrade grade) {
        return switch (grade) {
            case PSA10 -> new GradeKey("10", "PSA");
            case PSA9 -> new GradeKey("9", "PSA");
            case PSA8 -> new GradeKey("8", "PSA");
            case S -> new GradeKey("S", "");
            case A -> new GradeKey("A", "");
            case B -> new GradeKey("B", "");
        };
    }

    private record GradeKey(String grade, String company) {
    }

    // card_prices에는 시점별 체결 이력이 없고 "현재가(market) + 등락률(change_*_pct)"만 있다. 실제 체결 이력이 아니라,
    // market을 각 등락률만큼 거슬러 올라간 추정가 포인트를 만들어 반환한다 - PSA10/PSA9처럼 trades에 데이터가 거의
    // 없는 등급도 대략적인 추세선을 그릴 수 있게 하기 위한 용도(getPriceChart의 실거래 기반 차트와는 다른 성격).
    private static final List<PeriodDays> GRADE_CHART_PERIODS = List.of(
            new PeriodDays(180, CardPrice::getChange180dPct),
            new PeriodDays(90, CardPrice::getChange90dPct),
            new PeriodDays(30, CardPrice::getChange30dPct),
            new PeriodDays(14, CardPrice::getChange14dPct),
            new PeriodDays(7, CardPrice::getChange7dPct),
            new PeriodDays(1, CardPrice::getChange1dPct)
    );

    public List<CardPricePointResponse> getGradeChart(Long cardId, Long variantId, ListingGrade grade) {
        if (!cardRepository.existsById(cardId)) {
            throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
        }
        Long resolvedVariantId = variantId != null
                ? variantId
                : cardVariantRepository.findPrimaryVariantId(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        GradeKey gradeKey = toGradeKey(grade);
        CardPrice cardPrice = cardPriceRepository
                .findByVariantIdAndPriceTypeAndGradeAndCompany(resolvedVariantId, "graded", gradeKey.grade(), gradeKey.company())
                .orElse(null);

        if (cardPrice == null || cardPrice.getMarket() == null) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<CardPricePointResponse> points = new ArrayList<>();

        for (PeriodDays periodDays : GRADE_CHART_PERIODS) {
            BigDecimal changePct = periodDays.changePct().apply(cardPrice);
            BigDecimal divisor = changePct == null
                    ? null
                    : BigDecimal.ONE.add(changePct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            if (divisor == null || divisor.signum() == 0) {
                continue;
            }
            BigDecimal pastPrice = cardPrice.getMarket().divide(divisor, 2, RoundingMode.HALF_UP);
            points.add(new CardPricePointResponse(now.minusDays(periodDays.days()), pastPrice, cardPrice.getCurrency()));
        }

        points.add(new CardPricePointResponse(now, cardPrice.getMarket(), cardPrice.getCurrency()));

        return points;
    }

    private record PeriodDays(int days, Function<CardPrice, BigDecimal> changePct) {
    }

    // FR-PRICE-06: getStats()와 같은 방식(자체 AI등급 S, COMPLETED 거래, 최근 7일 vs 이전 7일 블록 평균 비교)을
    // 전체 카드로 확장해 등락률 상위/하위 10개 카드를 랭킹으로 뽑는다. card_prices(Scrydex 동기화)는 쓰지 않는다 —
    // 그 테이블은 PSA/CGC 같은 공인 등급만 있고 우리 자체 S등급 데이터가 없다(getStats와 동일한 이유).
    // 임시 계측 - Grafana 테스트용, 팀 논의 전 커밋 대상 아님
    @Timed(value = "price.ranking.duration")
    public List<PriceRankingResponse> getRanking(String type) {
        RankingType rankingType = RankingType.from(type);
        // 임시 계측 - Grafana 테스트용, 팀 논의 전 커밋 대상 아님
        meterRegistry.counter("price.ranking.requests", "type", rankingType.name()).increment();

        LocalDateTime recentFrom = LocalDateTime.now().minusDays(STATS_PERIOD_DAYS);
        LocalDateTime previousFrom = LocalDateTime.now().minusDays(STATS_PERIOD_DAYS * 2L);

        Map<Long, Double> recentAvgByCard = priceTradeStatsRepository
                .findAveragePricesByGradeSince(STATS_GRADE, TradeStatus.COMPLETED, recentFrom).stream()
                .collect(Collectors.toMap(
                        PriceTradeStatsRepository.CardAvgPriceView::getCardId,
                        PriceTradeStatsRepository.CardAvgPriceView::getAvgPrice));

        Map<Long, Double> previousAvgByCard = priceTradeStatsRepository
                .findAveragePricesByGradeBetween(STATS_GRADE, TradeStatus.COMPLETED, previousFrom, recentFrom).stream()
                .collect(Collectors.toMap(
                        PriceTradeStatsRepository.CardAvgPriceView::getCardId,
                        PriceTradeStatsRepository.CardAvgPriceView::getAvgPrice));

        // 두 블록 모두에 S등급 체결이 있는 카드만 등락률을 계산할 수 있다 (getStats의 "둘 중 하나라도 없으면 0" 규칙과 달리,
        // 랭킹에서는 변동률을 못 구하는 카드를 0으로 채워 순위에 끼워넣지 않고 아예 후보에서 제외한다).
        List<CardChangeRate> changes = recentAvgByCard.keySet().stream()
                .filter(previousAvgByCard::containsKey)
                .map(cardId -> {
                    BigDecimal recentAvgAmount = BigDecimal.valueOf(recentAvgByCard.get(cardId));
                    BigDecimal previousAvgAmount = BigDecimal.valueOf(previousAvgByCard.get(cardId));
                    BigDecimal diff = recentAvgAmount.subtract(previousAvgAmount);
                    BigDecimal changeRate = diff
                            .divide(previousAvgAmount, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new CardChangeRate(cardId, recentAvgAmount, changeRate, diff);
                })
                .sorted(rankingType == RankingType.RISE
                        ? Comparator.comparing(CardChangeRate::changeRate).reversed()
                        : Comparator.comparing(CardChangeRate::changeRate))
                .limit(RANKING_LIMIT)
                .toList();

        if (changes.isEmpty()) {
            return List.of();
        }

        Map<Long, Card> cardById = cardRepository
                .findAllById(changes.stream().map(CardChangeRate::cardId).toList()).stream()
                .collect(Collectors.toMap(Card::getId, card -> card));

        return changes.stream()
                .map(change -> {
                    Card card = cardById.get(change.cardId());
                    return new PriceRankingResponse(
                            change.cardId(),
                            card != null ? card.getName() : null,
                            card != null ? card.getImageSmall() : null,
                            change.recentAvgAmount().setScale(0, RoundingMode.HALF_UP).longValue(),
                            change.changeRate(),
                            change.diff().setScale(0, RoundingMode.HALF_UP).longValue()
                    );
                })
                .toList();
    }

    private record CardChangeRate(Long cardId, BigDecimal recentAvgAmount, BigDecimal changeRate, BigDecimal diff) {
    }
}
