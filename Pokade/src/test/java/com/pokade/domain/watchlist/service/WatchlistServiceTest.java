package com.pokade.domain.watchlist.service;

import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock WatchlistRepository watchlistRepository;
    @InjectMocks WatchlistService watchlistService;

    private Watchlist watchlist(Long userId, Long cardId) {
        return Watchlist.builder()
                .userId(userId).cardId(cardId).variantId(null)
                .targetBuyPrice(1000).targetSellPrice(null)
                .build();
    }

    // ===== 등록 =====
    @Test
    @DisplayName("등록: 목표가 둘 다 null이면 TARGET_PRICE_REQUIRED")
    void addWatchlist_targetPriceRequired() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, null, null);

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TARGET_PRICE_REQUIRED);
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 이미 등록된 카드면 DUPLICATE_WATCHLIST")
    void addWatchlist_duplicate() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(true);

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_WATCHLIST);
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 20개 이미 등록했으면 WATCHLIST_LIMIT_EXCEEDED")
    void addWatchlist_limitExceeded() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(20L);

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 검증 통과하면 저장 후 WatchlistResponse 반환")
    void addWatchlist_success() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, 2L, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        then(watchlistRepository).should().save(any(Watchlist.class));
        assertThat(response.cardId()).isEqualTo(1L);
        assertThat(response.variantId()).isEqualTo(2L);
        assertThat(response.targetBuyPrice()).isEqualTo(1000);
    }

    // ===== 목록 조회 =====
    @Test
    @DisplayName("목록 조회: 등록된 워치리스트 없으면 빈 리스트")
    void getWatchlist_empty() {
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of());

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("목록 조회: 등록된 워치리스트 개수만큼 반환")
    void getWatchlist_success() {
        given(watchlistRepository.findByUserId(1L))
                .willReturn(List.of(watchlist(1L, 10L), watchlist(1L, 20L)));

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result).hasSize(2);
    }

    // ===== 삭제 =====
    @Test
    @DisplayName("삭제: 존재하지 않으면 WATCHLIST_NOT_FOUND")
    void deleteWatchlist_notFound() {
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.deleteWatchlist(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WATCHLIST_NOT_FOUND);
        then(watchlistRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 정상 케이스면 repository.delete() 호출")
    void deleteWatchlist_success() {
        Watchlist target = watchlist(1L, 10L);
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        watchlistService.deleteWatchlist(1L, 1L);

        then(watchlistRepository).should().delete(target);
    }
}
