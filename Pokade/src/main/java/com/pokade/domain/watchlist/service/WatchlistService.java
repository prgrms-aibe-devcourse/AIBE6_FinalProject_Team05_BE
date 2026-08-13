package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    private static final long WATCHLIST_LIMIT = 20;

    private final WatchlistRepository watchlistRepository;
    private final PriceService priceService;
    private final CardRepository cardRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;

    @Transactional
    public WatchlistResponse addWatchlist(Long userId, WatchlistCreateRequest request) {
        if (request.targetBuyPrice() == null && request.targetSellPrice() == null) {
            throw new BusinessException(ErrorCode.TARGET_PRICE_REQUIRED);
        }

        if (watchlistRepository.existsByUserIdAndCardId(userId, request.cardId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_WATCHLIST);
        }

        if (watchlistRepository.countByUserId(userId) >= WATCHLIST_LIMIT) {
            throw new BusinessException(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
        }

        Watchlist watchlist = Watchlist.builder()
                .userId(userId)
                .cardId(request.cardId())
                .variantId(request.variantId())
                .targetBuyPrice(request.targetBuyPrice())
                .targetSellPrice(request.targetSellPrice())
                .build();

        Watchlist saved = watchlistRepository.save(watchlist);
        return WatchlistResponse.of(saved);
    }

    public List<WatchlistResponse> getWatchlist(Long userId) {
        List<Watchlist> watchlists = watchlistRepository.findByUserId(userId);
        if (watchlists.isEmpty()) {
            return List.of();
        }

        // 대표 variant 기준 시세만 배치 조회한다 - watchlist에 variantId가 지정된 항목도 지금은
        // 대표 variant 시세로 표시된다(개별 variant 시세 반영은 추후 필요해지면 확장).
        List<Long> cardIds = watchlists.stream().map(Watchlist::getCardId).distinct().toList();
        Map<Long, CardPriceSummaryResponse> priceByCardId = priceService.getSummaries(cardIds, null, true)
                .stream()
                .collect(Collectors.toMap(CardPriceSummaryResponse::cardId, Function.identity()));
        Map<Long, Card> cardById = cardRepository.findAllById(cardIds)
                .stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        // "목표가 도달"은 지금 시세가 목표가보다 높은지/낮은지가 아니라, 체결가가 그동안 오르내리며
        // 그 목표가를 한 번이라도 지나간 적이 있는지로 판정한다 - 그래서 매수/매도 구분 없이 전체 기간
        // 체결가의 최저~최고 구간에 목표가가 들어오는지만 본다(사용자 요청, 2026-08-13).
        Map<Long, PriceTradeStatsRepository.CardPriceRangeView> rangeByCardId =
                priceTradeStatsRepository.findPriceRangesByCardIds(cardIds, null, TradeStatus.COMPLETED)
                        .stream()
                        .collect(Collectors.toMap(PriceTradeStatsRepository.CardPriceRangeView::getCardId, Function.identity()));
        // "등락" 배지용 - 최근 7일 vs 이전 7일 S등급 평균 체결가 비교(%). getStats()/getRanking()과 같은 기준.
        Map<Long, BigDecimal> changeRateByCardId = priceService.getChangeRates(cardIds);

        return watchlists.stream()
                .map(watchlist -> WatchlistResponse.withPrice(
                        watchlist,
                        cardById.get(watchlist.getCardId()),
                        priceByCardId.get(watchlist.getCardId()),
                        changeRateByCardId.get(watchlist.getCardId()),
                        isTargetReached(watchlist, rangeByCardId.get(watchlist.getCardId()))))
                .toList();
    }

    private boolean isTargetReached(Watchlist watchlist, PriceTradeStatsRepository.CardPriceRangeView range) {
        if (range == null || range.getMinPrice() == null || range.getMaxPrice() == null) {
            return false;
        }
        Integer targetBuyPrice = watchlist.getTargetBuyPrice();
        if (targetBuyPrice != null && range.getMinPrice() <= targetBuyPrice && targetBuyPrice <= range.getMaxPrice()) {
            return true;
        }
        Integer targetSellPrice = watchlist.getTargetSellPrice();
        return targetSellPrice != null
                && range.getMinPrice() <= targetSellPrice && targetSellPrice <= range.getMaxPrice();
    }

    @Transactional
    public void deleteWatchlist(Long userId, Long watchlistId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        watchlistRepository.delete(watchlist);
    }
}
