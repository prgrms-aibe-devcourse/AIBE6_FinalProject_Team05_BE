package com.pokade.domain.trade.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.point.service.PointService;
import com.pokade.domain.trade.dto.MyTradeResponse;
import com.pokade.domain.trade.dto.MyTradeSearchCondition;
import com.pokade.domain.trade.dto.TradeCreateRequest;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Payment;
import com.pokade.domain.trade.entity.PaymentMethod;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.PaymentRepository;
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
    private final PointService pointService;

    private TradeResponse toResponse(Trade trade) {
        String cardName = cardRepository.findById(trade.getListing().getCardId())
                .map(Card::getName)
                .orElse(null);
        return TradeResponse.of(trade, cardName);
    }

    @Transactional
    public TradeResponse createTrade(Long buyerId, TradeCreateRequest request) {
        userAccessChecker.assertWritable(buyerId);

        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        userAccessChecker.assertWritable(listing.getSellerId());

        if (listing.getSellerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_PURCHASE_NOT_ALLOWED);
        }

        int updated = listingRepository.markAsTrading(listing.getId());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TRADE_CONFLICT);
        }

        Trade trade = tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(buyerId)
                        .price(listing.getPrice())
                        .build()
        );

        // 포인트 잔액이 부족하면 여기서 BusinessException(INSUFFICIENT_POINT_BALANCE)이 발생하고,
        // 같은 트랜잭션 안이라 위의 markAsTrading/Trade 저장도 함께 롤백된다.
        pointService.use(buyerId, trade.getPrice(), trade.getId());

        paymentRepository.save(
                Payment.builder()
                        .trade(trade)
                        .buyerId(buyerId)
                        .amount(trade.getPrice())
                        .method(PaymentMethod.POINT)
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

        // 거래 생성 시 buyer의 포인트가 이미 차감됐으므로, 취소 성공 시 그만큼 돌려준다.
        // trade.cancel()이 이미 취소/완료된 거래는 걸러주므로 같은 거래에 대해 두 번 환불될 일은 없다.
        pointService.refund(trade.getBuyerId(), trade.getPrice(), trade.getId());

        return toResponse(trade);
    }
}
