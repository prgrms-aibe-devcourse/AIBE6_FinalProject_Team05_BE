package com.pokade.domain.watchlist.service;

import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return watchlists.stream()
                .map(watchlist -> WatchlistResponse.withPrice(watchlist, priceByCardId.get(watchlist.getCardId())))
                .toList();
    }

    @Transactional
    public void deleteWatchlist(Long userId, Long watchlistId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        watchlistRepository.delete(watchlist);
    }
}
