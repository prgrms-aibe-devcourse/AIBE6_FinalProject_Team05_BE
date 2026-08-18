package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.dto.WatchlistUpdateRequest;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        validateAtLeastOneTargetPrice(request.targetBuyPrice(), request.targetSellPrice());

        // 동시 등록 요청에서 "중복 체크 + 20개 제한 체크 + 저장" 구간이 원자적이도록, 같은 유저의 요청만
        // 트랜잭션 종료까지 직렬화한다(다른 유저는 영향 없음).
        watchlistRepository.acquireUserLock(userId);

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

        // 위 잠금으로 정상 경로에서는 걸릴 일이 없지만, 방어적으로 DB UNIQUE 제약 위반도 안전하게 변환한다.
        try {
            Watchlist saved = watchlistRepository.save(watchlist);
            return WatchlistResponse.of(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_WATCHLIST);
        }
    }

    public List<WatchlistResponse> getWatchlist(Long userId) {
        List<Watchlist> watchlists = watchlistRepository.findByUserId(userId);
        if (watchlists.isEmpty()) {
            return List.of();
        }
        
        List<Long> cardIds = watchlists.stream().map(Watchlist::getCardId).distinct().toList();
        Map<Long, CardPriceSummaryResponse> priceByCardId = priceService.getSummaries(cardIds, null, true)
                .stream()
                .collect(Collectors.toMap(CardPriceSummaryResponse::cardId, Function.identity()));
        Map<Long, Card> cardById = cardRepository.findAllById(cardIds)
                .stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

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
        return resolveReachedTargetPrice(watchlist, range) != null;
    }

    // 목표가(구매/판매) 도달 판정 - 도달한 목표가 값을 반환(없으면 null).
    // isTargetReached()와 동일한 판정 로직을 재사용 가능한 형태로 추출한 것 (WatchlistTargetPriceNoticeService에서 재사용).
    Integer resolveReachedTargetPrice(Watchlist watchlist, PriceTradeStatsRepository.CardPriceRangeView range) {
        if (range == null || range.getMinPrice() == null || range.getMaxPrice() == null) {
            return null;
        }
        Integer targetBuyPrice = watchlist.getTargetBuyPrice();
        if (targetBuyPrice != null && range.getMinPrice() <= targetBuyPrice && targetBuyPrice <= range.getMaxPrice()) {
            return targetBuyPrice;
        }
        Integer targetSellPrice = watchlist.getTargetSellPrice();
        if (targetSellPrice != null && range.getMinPrice() <= targetSellPrice && targetSellPrice <= range.getMaxPrice()) {
            return targetSellPrice;
        }
        return null;
    }

    @Transactional
    public WatchlistResponse updateWatchlist(Long userId, Long watchlistId, WatchlistUpdateRequest request) {
        validateAtLeastOneTargetPrice(request.targetBuyPrice(), request.targetSellPrice());

        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        watchlist.updateTargetPrices(request.targetBuyPrice(), request.targetSellPrice());
        return WatchlistResponse.of(watchlist);
    }

    private void validateAtLeastOneTargetPrice(Integer targetBuyPrice, Integer targetSellPrice) {
        if (targetBuyPrice == null && targetSellPrice == null) {
            throw new BusinessException(ErrorCode.TARGET_PRICE_REQUIRED);
        }
    }

    @Transactional
    public void deleteWatchlist(Long userId, Long watchlistId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        watchlistRepository.delete(watchlist);
    }
}
