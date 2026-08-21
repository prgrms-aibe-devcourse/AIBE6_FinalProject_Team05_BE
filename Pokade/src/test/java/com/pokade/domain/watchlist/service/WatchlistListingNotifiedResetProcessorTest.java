package com.pokade.domain.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;

@ExtendWith(MockitoExtension.class)
class WatchlistListingNotifiedResetProcessorTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private WatchlistListingNotifiedResetProcessor processor;

    private Watchlist notifiedWatchlist(Long id, Long cardId, Long variantId) {
        Watchlist watchlist = Watchlist.builder()
                .userId(id).cardId(cardId).variantId(variantId).targetBuyPrice(1000).build();
        ReflectionTestUtils.setField(watchlist, "id", id);
        watchlist.markAsListingNotified();
        return watchlist;
    }

    @Test
    @DisplayName("활성 매물이 남아있으면 리셋하지 않는다")
    void process_keepsNotifiedWhenActiveListingRemains() {
        Watchlist watchlist = notifiedWatchlist(10L, 1L, 100L);
        given(watchlistRepository.findById(10L)).willReturn(Optional.of(watchlist));
        given(listingRepository.countByCardIdAndVariantIdAndStatus(1L, 100L, ListingStatus.ACTIVE)).willReturn(1L);

        processor.process(10L, 100L);

        assertThat(watchlist.isListingNotified()).isTrue();
        then(watchlistRepository).should(never()).resetListingNotifiedIfTrue(any());
    }

    @Test
    @DisplayName("활성 매물이 0개면 리셋한다")
    void process_resetsWhenNoActiveListingRemains() {
        Watchlist watchlist = notifiedWatchlist(10L, 1L, 100L);
        given(watchlistRepository.findById(10L)).willReturn(Optional.of(watchlist));
        given(listingRepository.countByCardIdAndVariantIdAndStatus(1L, 100L, ListingStatus.ACTIVE)).willReturn(0L);
        given(watchlistRepository.resetListingNotifiedIfTrue(10L)).willReturn(1);

        processor.process(10L, 100L);

        assertThat(watchlist.isListingNotified()).isFalse();
    }

    @Test
    @DisplayName("이미 다른 트랜잭션이 리셋을 선점(claim 실패)했으면 엔티티를 건드리지 않는다")
    void process_skipsWhenResetClaimFails() {
        Watchlist watchlist = notifiedWatchlist(10L, 1L, 100L);
        given(watchlistRepository.findById(10L)).willReturn(Optional.of(watchlist));
        given(listingRepository.countByCardIdAndVariantIdAndStatus(1L, 100L, ListingStatus.ACTIVE)).willReturn(0L);
        given(watchlistRepository.resetListingNotifiedIfTrue(10L)).willReturn(0);

        processor.process(10L, 100L);

        assertThat(watchlist.isListingNotified()).isTrue();
    }

    @Test
    @DisplayName("워치리스트가 삭제됐으면(재조회 결과 없음) 아무것도 하지 않는다")
    void process_skipsWhenWatchlistNotFound() {
        given(watchlistRepository.findById(10L)).willReturn(Optional.empty());

        processor.process(10L, 100L);

        then(listingRepository).should(never()).countByCardIdAndVariantIdAndStatus(any(), any(), any());
        then(watchlistRepository).should(never()).resetListingNotifiedIfTrue(any());
    }

    @Test
    @DisplayName("이미 listingNotified가 false면(그 사이 다른 경로로 리셋됨) 아무것도 하지 않는다")
    void process_skipsWhenAlreadyNotNotified() {
        Watchlist watchlist = Watchlist.builder().userId(10L).cardId(1L).targetBuyPrice(1000).build();
        ReflectionTestUtils.setField(watchlist, "id", 10L);
        given(watchlistRepository.findById(10L)).willReturn(Optional.of(watchlist));

        processor.process(10L, 100L);

        then(listingRepository).should(never()).countByCardIdAndVariantIdAndStatus(any(), any(), any());
    }
}
