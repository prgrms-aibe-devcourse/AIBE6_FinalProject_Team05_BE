package com.pokade.domain.watchlist.service;

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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    private static final long WATCHLIST_LIMIT = 20;

    private final WatchlistRepository watchlistRepository;

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
        return watchlistRepository.findByUserId(userId)
                .stream()
                .map(WatchlistResponse::of)
                .toList();
    }

    @Transactional
    public void deleteWatchlist(Long userId, Long watchlistId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        watchlistRepository.delete(watchlist);
    }
}
