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
import com.pokade.domain.trade.repository.PaymentRepository;
import com.pokade.domain.trade.repository.TradeOrderRepository;
import com.pokade.domain.trade.repository.TradeRepository;
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

    private final ListingRepository listingRepository;
    private final TradeRepository tradeRepository;
    private final PaymentRepository paymentRepository;
    private final CardRepository cardRepository;
    private final UserAccessChecker userAccessChecker;
    private final TradeOrderRepository tradeOrderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PointService pointService;
    private final PortfolioService portfolioService;

    private TradeResponse toResponse(Trade trade) {
        String cardName = cardRepository.findById(trade.getListing().getCardId())
                .map(Card::getName)
                .orElse(null);
        return TradeResponse.of(trade, cardName);
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

        String orderId = UUID.randomUUID().toString();
        tradeOrderRepository.save(TradeOrder.builder()
                .orderId(orderId)
                .buyerId(buyerId)
                .listingId(listing.getId())
                .amount(listing.getPrice())
                .build());

        return new TradeReadyResponse(orderId, listing.getPrice());
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
        if (order.getAmount() != amount) {
            // 리다이렉트로 전달된 금액이 최초 요청 금액과 다르면 위변조 의심 - 토스 승인 API를 호출하지 않고 거부한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청 금액이 일치하지 않습니다.");
        }

        try {
            tossPaymentClient.confirmPayment(paymentKey, orderId, order.getAmount());
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
            // 결제는 승인됐지만 그 사이 다른 구매자가 먼저 사간 경우 - 승인된 결제를 즉시 취소해 환불한다.
            tossPaymentClient.cancelPayment(paymentKey, "이미 판매된 매물입니다.");
            tradeOrderRepository.markFailedIfPending(orderId);
            throw new BusinessException(ErrorCode.TRADE_CONFLICT);
        }

        Trade trade = tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(buyerId)
                        .price(listing.getPrice())
                        .build()
        );

        paymentRepository.save(
                Payment.builder()
                        .trade(trade)
                        .buyerId(buyerId)
                        .amount(trade.getPrice())
                        .method(PaymentMethod.CARD)
                        .tossPaymentKey(paymentKey)
                        .build()
        );

        order.markConfirmed();

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

        // 구매 시점에 이미 토스로 실제 결제가 완료돼 에스크로로 잡혀있으므로, 취소 성공 시
        // 저장해둔 paymentKey로 토스 결제취소(환불) API를 실제로 호출한다.
        Payment payment = paymentRepository.findByTradeId(trade.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_FAILED));
        tossPaymentClient.cancelPayment(payment.getTossPaymentKey(), "거래 취소");
        payment.refund();

        return toResponse(trade);
    }
}
