package com.pokade.domain.price.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.ListingRepository;
import com.pokade.domain.listing.ListingStatus;
import com.pokade.domain.price.ChartPeriod;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.trade.TradeRepository;
import com.pokade.domain.trade.TradeStatus;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceService {

    private static final String CURRENCY = "KRW";
    private static final int RECENT_TRADES_LIMIT = 20;

    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final ListingRepository listingRepository;
    private final BuyOfferRepository buyOfferRepository;
    private final TradeRepository tradeRepository;

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
}
