package com.pokade.domain.trade.service;

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

    @Transactional
    public TradeResponse createTrade(Long buyerId, TradeCreateRequest request) {
        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

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

        return TradeResponse.of(trade);
    }

    public TradeResponse getTrade(Long userId, Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        boolean isParticipant = trade.getBuyerId().equals(userId)
                || trade.getListing().getSellerId().equals(userId);
        if (!isParticipant) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return TradeResponse.of(trade);
    }

    @Transactional
    public TradeResponse confirmTrade(Long buyerId, Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        if (!trade.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        trade.complete();

        return TradeResponse.of(trade);
    }

    @Transactional
    public TradeResponse cancelTrade(Long userId, Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        boolean isParticipant = trade.getBuyerId().equals(userId)
                || trade.getListing().getSellerId().equals(userId);
        if (!isParticipant) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        trade.cancel();

        return TradeResponse.of(trade);
    }
}
