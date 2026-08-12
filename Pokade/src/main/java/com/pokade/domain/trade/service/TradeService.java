package com.pokade.domain.trade.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.dto.TradeCreateRequest;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Payment;
import com.pokade.domain.trade.entity.PaymentMethod;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.repository.PaymentRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeService {

    private final ListingRepository listingRepository;
    private final TradeRepository tradeRepository;
    private final PaymentRepository paymentRepository;
    private final CardRepository cardRepository;
    private final UserAccessChecker userAccessChecker;

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

        // TODO: 실제 PG 연동 전까지는 결제가 항상 성공한다고 가정 (에스크로 보류 상태로만 생성)
        paymentRepository.save(
                Payment.builder()
                        .trade(trade)
                        .buyerId(buyerId)
                        .amount(trade.getPrice())
                        .method(PaymentMethod.CARD)
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

        return toResponse(trade);
    }
}
