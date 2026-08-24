package com.pokade.domain.trade.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.point.client.TossPaymentClient;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.portfolio.service.PortfolioService;
import com.pokade.domain.trade.dto.MyTradeResponse;
import com.pokade.domain.trade.dto.MyTradeSearchCondition;
import com.pokade.domain.trade.dto.TradeReadyRequest;
import com.pokade.domain.trade.dto.TradeReadyResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Payment;
import com.pokade.domain.trade.entity.PaymentMethod;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeOrder;
import com.pokade.domain.trade.entity.TradeOrderStatus;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.trade.repository.PaymentRepository;
import com.pokade.domain.trade.repository.TradeOrderRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeService {

    // 즉시구매 결제 시 상품가에 더하는 고정 배송비(KRW) - domain.price의 구매입찰 결제(SHIPPING_FEE)와
    // 동일한 값/성격이며, 두 도메인이 각자 자기 상수로 갖는다(공유 상수 모듈은 아직 없음).
    private static final int SHIPPING_FEE = 3000;

    private final ListingRepository listingRepository;
    private final TradeRepository tradeRepository;
    private final PaymentRepository paymentRepository;
    private final CardRepository cardRepository;
    private final UserAccessChecker userAccessChecker;
    private final UserRepository userRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PointService pointService;
    private final PortfolioService portfolioService;
    // #392: 구매확정(정산) 시 판매자에게 보내는 알림 - confirmTrade()에서만 쓴다.
    private final NotificationService notificationService;

    private TradeResponse toResponse(Trade trade) {
        Card card = cardRepository.findById(trade.getListing().getCardId()).orElse(null);
        Integer pointsUsed = paymentRepository.findByTradeId(trade.getId())
                .map(Payment::getPointsUsed)
                .orElse(null);
        return TradeResponse.of(
                trade,
                card != null ? card.getName() : null,
                card != null ? card.getImageSmall() : null,
                pointsUsed);
    }

    // 결제창을 띄우기 전에 주문을 먼저 PENDING으로 기록한다 - 매물은 아직 잠그지 않는다(TRADING으로
    // 바꾸지 않는다). 여기서 잠가버리면 구매자가 결제를 포기했을 때 매물이 영구히 묶여버린다. 실제
    // 잠금은 결제가 실제로 승인된 뒤 confirmPurchase()에서 수행한다.
    @Transactional
    public TradeReadyResponse ready(Long buyerId, TradeReadyRequest request) {
        userAccessChecker.assertWritable(buyerId);

        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        userAccessChecker.assertWritable(listing.getSellerId());

        if (listing.getSellerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_PURCHASE_NOT_ALLOWED);
        }

        int totalAmount = listing.getPrice() + SHIPPING_FEE;
        int pointsToUse = request.pointsToUse();
        if (pointsToUse > totalAmount) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "포인트 사용액이 결제 금액보다 클 수 없습니다.");
        }
        if (pointsToUse > 0) {
            // 실제 차감은 결제 승인 시점(confirmPurchase)에서 한다 - 여기서는 구매를 포기하거나
            // 결제를 완료하지 않아도 포인트가 미리 묶이지 않도록, 잔액이 충분한지만 미리 확인해 준다.
            User buyer = userRepository.findById(buyerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (buyer.getPointBalance() < pointsToUse) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_POINT_BALANCE);
            }
        }

        String orderId = UUID.randomUUID().toString();
        int paymentAmount = totalAmount - pointsToUse;
        tradeOrderRepository.save(TradeOrder.builder()
                .orderId(orderId)
                .buyerId(buyerId)
                .listingId(listing.getId())
                .amount(totalAmount)
                .shippingFee(SHIPPING_FEE)
                .pointsUsed(pointsToUse)
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .recipientAddress(request.recipientAddress())
                .build());

        return new TradeReadyResponse(orderId, paymentAmount);
    }

    // 결제 승인 콜백 처리 - 토스 승인이 끝난 뒤에야 매물 잠금을 시도한다. 그 사이 다른 구매자가
    // 먼저 사갔다면(markAsTrading 실패) 이미 승인된 결제를 즉시 취소(환불)하고 TRADE_CONFLICT를 던진다.
    @Transactional
    public TradeResponse confirmPurchase(Long buyerId, String paymentKey, String orderId, long amount) {
        TradeOrder order = tradeOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_ORDER_NOT_FOUND));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (order.getStatus() != TradeOrderStatus.PENDING) {
            // 이미 처리된 주문이면 토스 승인 API를 다시 호출하지 않고 즉시 거부한다 (중복 콜백 방어).
            throw new BusinessException(ErrorCode.TRADE_ORDER_ALREADY_PROCESSED);
        }
        if (order.getPaymentAmount() != amount) {
            // 리다이렉트로 전달된 금액이 최초 요청 금액과 다르면 위변조 의심 - 토스 승인 API를 호출하지 않고 거부한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 금액이 일치하지 않습니다.");
        }
        if (order.getPaymentAmount() > 0 && (paymentKey == null || paymentKey.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "paymentKey는 필수입니다.");
        }

        // 포인트 사용액이 있으면 토스 승인보다 먼저 차감한다 - 이후 토스 승인이 실패하거나 매물이
        // 이미 팔려있어 예외가 다시 던져지면 이 메서드의 트랜잭션 전체가 롤백되므로(pointService.use()도
        // 같은 트랜잭션에 참여), 별도 환불 호출 없이도 방금 차감한 포인트가 함께 되돌아간다.
        try {
            if (order.getPointsUsed() > 0) {
                pointService.use(buyerId, order.getPointsUsed(), null);
            }
            if (order.getPaymentAmount() > 0) {
                tossPaymentClient.confirmPayment(paymentKey, orderId, order.getPaymentAmount());
            }
        } catch (BusinessException e) {
            // 이 메서드는 결국 예외를 다시 던져 전체 트랜잭션이 롤백되므로, 실패 기록은 별도 빈(리포지토리)의
            // REQUIRES_NEW 트랜잭션으로 즉시 커밋해야 한다 - 같은 트랜잭션 안에서 엔티티만 바꾸면 유실된다.
            tradeOrderRepository.markFailedIfPending(orderId);
            throw e;
        }

        Listing listing = listingRepository.findById(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        int updated = listingRepository.markAsTrading(listing.getId());
        if (updated == 0) {
            // 결제는 승인됐지만 그 사이 다른 구매자가 먼저 사간 경우 - 승인된 결제(있었다면)를 즉시
            // 취소해 환불한다. 방금 차감한 포인트는 아래 throw로 전체 트랜잭션이 롤백되며 함께 되돌아간다.
            if (order.getPaymentAmount() > 0) {
                tossPaymentClient.cancelPayment(paymentKey, "이미 판매된 매물입니다.");
            }
            tradeOrderRepository.markFailedIfPending(orderId);
            throw new BusinessException(ErrorCode.TRADE_CONFLICT);
        }

        Trade trade = tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(buyerId)
                        .price(listing.getPrice())
                        .recipientName(order.getRecipientName())
                        .recipientPhone(order.getRecipientPhone())
                        .recipientAddress(order.getRecipientAddress())
                        .build()
        );

        // Payment.amount는 실제로 토스에 결제(에스크로)된 금액 - 배송비가 포함된 order.getPaymentAmount()이지,
        // 판매자 정산 기준인 trade.getPrice()(상품가만)가 아니다. 판매자 정산은 계속 trade.getPrice()로
        // 이뤄지므로(confirmTrade()의 pointService.settle() 참고) 배송비/포인트 사용액이 정산에 섞이지 않는다.
        paymentRepository.save(
                Payment.builder()
                        .trade(trade)
                        .buyerId(buyerId)
                        .amount(order.getPaymentAmount())
                        .pointsUsed(order.getPointsUsed())
                        .method(PaymentMethod.CARD)
                        .tossPaymentKey(order.getPaymentAmount() > 0 ? paymentKey : null)
                        .build()
        );

        order.markConfirmed();

        return toResponse(trade);
    }

    // domain.price의 "구매입찰 즉시판매" 전용 진입점 - 이미 결제(토스 에스크로)가 끝난 구매입찰에
    // 판매자가 방금 등록한 매물을 즉시 매칭시킨다. TradeOrder를 거치지 않으므로(결제를 다시 받지 않음)
    // confirmPurchase()와 별도 메서드로 둔다. BuyOffer 타입을 직접 참조하지 않고 이미 검증된 값만
    // 받아서, 이 서비스가 domain.price의 엔티티를 몰라도 되게 한다.
    @Transactional
    public TradeResponse createMatchedTrade(
            Long listingId, Long buyerId, Integer price, Integer paymentAmount,
            String recipientName, String recipientPhone, String recipientAddress,
            String tossPaymentKey, Integer pointsUsed
    ) {
        int updated = listingRepository.markAsTrading(listingId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TRADE_CONFLICT);
        }
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        Trade trade = tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(buyerId)
                        .price(price)
                        .recipientName(recipientName)
                        .recipientPhone(recipientPhone)
                        .recipientAddress(recipientAddress)
                        .build()
        );

        // Payment.amount는 confirmPurchase()와 동일하게 실제 결제(에스크로+포인트)된 금액인
        // paymentAmount를 저장한다 - trade.getPrice()(=price, 상품가만)가 아니다.
        paymentRepository.save(
                Payment.builder()
                        .trade(trade)
                        .buyerId(buyerId)
                        .amount(paymentAmount)
                        .pointsUsed(pointsUsed)
                        .method(PaymentMethod.CARD)
                        .tossPaymentKey(tossPaymentKey)
                        .build()
        );

        return toResponse(trade);
    }

    public TradeResponse getTrade(Long userId, Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        boolean isParticipant = trade.getBuyerId().equals(userId)
                || trade.getListing().getSellerId().equals(userId);
        if (!isParticipant) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return toResponse(trade);
    }

    public Page<MyTradeResponse> getMyTrades(Long userId, MyTradeSearchCondition condition, Pageable pageable) {
        Page<Trade> trades = tradeRepository.findMyTrades(
                userId,
                condition.includeBuy(),
                condition.includeSell(),
                condition.statusesOrAll(),
                condition.fromDateTime(),
                condition.toDateTimeExclusive(),
                pageable);

        Set<Long> cardIds = trades.getContent().stream()
                .map(trade -> trade.getListing().getCardId())
                .collect(Collectors.toSet());

        // toResponse()처럼 건건이 조회하면 목록 크기만큼 쿼리가 나가므로 한 번에 모아 온다
        Map<Long, Card> cards = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

        return trades.map(trade ->
                MyTradeResponse.of(trade, userId, cards.get(trade.getListing().getCardId())));
    }

    @Transactional
    public TradeResponse confirmTrade(Long buyerId, Long tradeId) {
        userAccessChecker.assertWritable(buyerId);

        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        if (!trade.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        trade.complete();

        // 구매확정 = 에스크로 해제 시점 - 플랫폼이 들고 있던 결제 금액을 판매자에게 정산한다.
        // 실제 계좌 이체 연동 전까지는 판매자 포인트 잔액으로 정산한다.
        Payment payment = paymentRepository.findByTradeId(trade.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_FAILED));
        payment.settle();
        pointService.settle(trade.getListing().getSellerId(), trade.getPrice(), trade.getId());

        Listing listing = trade.getListing();
        portfolioService.addFromCompletedTrade(
                buyerId,
                trade.getId(),
                listing.getCardId(),
                listing.getVariantId(),
                trade.getPrice()
        );

        // #392: 판매자에게 정산 완료를 알린다. 판매자 입장에서는 자기가 하지 않은 액션(구매자의 구매확정)으로
        // 잔액이 늘어나는 지점이라, 앱 안에 알림이 없으면 정산 사실을 알 방법이 없었다.
        // 카드명 조회가 추가 쿼리로 보이지만, 바로 아래 toResponse()가 같은 트랜잭션에서 같은 id를 다시
        // 조회하므로 둘 중 하나는 영속성 컨텍스트 1차 캐시에서 해결된다 - DB 왕복 횟수는 그대로다.
        notificationService.createTradeConfirmedNotification(
                listing.getSellerId(),
                listing.getCardId(),
                cardRepository.findById(listing.getCardId()).map(Card::getName).orElse("카드"),
                trade.getPrice());

        return toResponse(trade);
    }

    // 판매자가 플랫폼으로 발송 처리 (판매자 본인 액션)
    @Transactional
    public TradeResponse shipTrade(Long sellerId, Long tradeId) {
        userAccessChecker.assertWritable(sellerId);

        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        if (!trade.getListing().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        trade.shipToPlatform();

        return toResponse(trade);
    }

    // 관리자 검수/배송 처리 대기 목록: 발송됨(검수 대기), 검수됨(배송 대기) 거래
    public List<TradeResponse> getPendingTrades() {
        return tradeRepository.findByStatusInOrderByCreatedAtAsc(
                        List.of(TradeStatus.SHIPPED_TO_PLATFORM, TradeStatus.INSPECTED))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 관리자 페이지에서 호출 — 인가(관리자 권한 확인)는 호출하는 쪽(관리자 도메인)의 책임.
    @Transactional
    public TradeResponse markInspected(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        trade.markInspected();

        return toResponse(trade);
    }

    // 관리자 페이지에서 호출 — 인가(관리자 권한 확인)는 호출하는 쪽(관리자 도메인)의 책임.
    @Transactional
    public TradeResponse markDelivered(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        trade.markDelivered();

        return toResponse(trade);
    }

    @Transactional
    public TradeResponse cancelTrade(Long userId, Long tradeId) {
        userAccessChecker.assertWritable(userId);

        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        boolean isParticipant = trade.getBuyerId().equals(userId)
                || trade.getListing().getSellerId().equals(userId);
        if (!isParticipant) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        trade.cancel();

        // 취소된 거래의 매물을 다시 판매 가능(ACTIVE) 상태로 되돌린다 — 안 그러면 TRADING에 영원히
        // 묶여서 마켓/호가창 어디서도 조회되지 않는 좀비 매물이 된다.
        listingRepository.revertToActiveIfTrading(trade.getListing().getId());

        // 구매 시점에 이미 토스로 실제 결제가 완료돼 에스크로로 잡혀있으므로, 취소 성공 시
        // 저장해둔 paymentKey로 토스 결제취소(환불) API를 실제로 호출한다. 포인트로 전액을
        // 충당한 결제는 tossPaymentKey가 없어 토스 호출 자체가 필요 없다.
        Payment payment = paymentRepository.findByTradeId(trade.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_FAILED));
        if (payment.getTossPaymentKey() != null) {
            tossPaymentClient.cancelPayment(payment.getTossPaymentKey(), "거래 취소");
        }
        if (payment.getPointsUsed() != null && payment.getPointsUsed() > 0) {
            pointService.refund(trade.getBuyerId(), payment.getPointsUsed(), trade.getId());
        }
        payment.refund();

        return toResponse(trade);
    }
}
