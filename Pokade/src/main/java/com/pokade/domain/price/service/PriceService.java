package com.pokade.domain.price.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.price.ChartPeriod;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.repository.BuyOfferRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // N+1을 피하기 위한 배치 버전.
    public List<CardPriceSummaryResponse> getSummaries(List<Long> cardIds) {
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
                : listingRepository.findLowestActivePricesByVariantIds(variantIds, ListingStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(
                                ListingRepository.VariantPriceView::getVariantId,
                                ListingRepository.VariantPriceView::getPrice));

        Map<Long, Integer> sellPriceByVariant = variantIds.isEmpty()
                ? Map.of()
                : buyOfferRepository.findHighestActivePricesByVariantIds(variantIds).stream()
                        .collect(Collectors.toMap(
                                BuyOfferRepository.VariantPriceView::getVariantId,
                                BuyOfferRepository.VariantPriceView::getPrice));

        return distinctCardIds.stream()
                .map(cardId -> {
                    Long variantId = primaryVariantByCard.get(cardId);
                    Integer buyPrice = variantId != null ? buyPriceByVariant.get(variantId) : null;
                    Integer sellPrice = variantId != null ? sellPriceByVariant.get(variantId) : null;
                    return new CardPriceSummaryResponse(cardId, buyPrice, sellPrice, CURRENCY);
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
}
