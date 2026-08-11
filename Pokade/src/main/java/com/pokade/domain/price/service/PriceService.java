package com.pokade.domain.price.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.price.ChartPeriod;
import com.pokade.domain.price.RankingType;
import com.pokade.domain.price.StatsPeriod;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.price.repository.PriceCardStatsRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final ListingRepository listingRepository;
    private final BuyOfferRepository buyOfferRepository;
    private final TradeRepository tradeRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;
    private final PriceCardStatsRepository priceCardStatsRepository;

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

        return distinctCardIds.stream()
                .map(cardId -> {
                    Long variantId = primaryVariantByCard.get(cardId);
                    Integer buyPrice = variantId != null ? buyPriceByVariant.get(variantId) : null;
                    Integer sellPrice = variantId != null ? sellPriceByVariant.get(variantId) : null;
                    Integer recentTradePrice = recentTradePriceByCard.get(cardId);
                    return new CardPriceSummaryResponse(cardId, buyPrice, sellPrice, recentTradePrice, CURRENCY);
                })
                .toList();
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

    // FR-PRICE-06: getStats()와 같은 방식(자체 AI등급 S, COMPLETED 거래, 최근 7일 vs 이전 7일 블록 평균 비교)을
    // 전체 카드로 확장해 등락률 상위/하위 10개 카드를 랭킹으로 뽑는다. card_prices(Scrydex 동기화)는 쓰지 않는다 —
    // 그 테이블은 PSA/CGC 같은 공인 등급만 있고 우리 자체 S등급 데이터가 없다(getStats와 동일한 이유).
    public List<PriceRankingResponse> getRanking(String type) {
        RankingType rankingType = RankingType.from(type);

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
