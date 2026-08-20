package com.pokade.domain.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.event.ListingCreatedEvent;

@ExtendWith(MockitoExtension.class)
class WatchlistListingAvailableNoticeListenerTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardNameKoResolver cardNameKoResolver;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private WatchlistListingAvailableNoticeListener listener;

    private Watchlist watchlist(Long id, Long cardId) {
        Watchlist watchlist = Watchlist.builder().userId(id).cardId(cardId).targetBuyPrice(1000).build();
        org.springframework.test.util.ReflectionTestUtils.setField(watchlist, "id", id);
        return watchlist;
    }

    @Test
    @DisplayName("이미 매물이 있던 카드(count != 1)면 워치리스트 조회 자체를 하지 않는다")
    void onListingCreated_skipsWhenNotUniqueActiveListing() {
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(2L);

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        then(watchlistRepository).should(never()).findByCardIdAndListingNotifiedFalse(any());
        then(notificationService).should(never()).createListingAvailableNotification(any(), any(), any());
    }

    @Test
    @DisplayName("유일한 활성 매물이지만 워치리스트 등록자가 없으면 알림을 만들지 않는다")
    void onListingCreated_noWatchersMeansNoNotification() {
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(1L);
        given(watchlistRepository.findByCardIdAndListingNotifiedFalse(1L)).willReturn(List.of());

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        then(cardRepository).should(never()).findById(any());
        then(notificationService).should(never()).createListingAvailableNotification(any(), any(), any());
    }

    @Test
    @DisplayName("유일한 활성 매물이고 워치리스트 등록자가 있으면 알림을 생성하고 listingNotified를 true로 표시한다")
    void onListingCreated_createsNotificationAndMarksListingNotified() {
        Watchlist w1 = watchlist(10L, 1L);
        Card card = Card.builder().id(1L).name("Charizard").build();
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(1L);
        given(watchlistRepository.findByCardIdAndListingNotifiedFalse(1L)).willReturn(List.of(w1));
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardNameKoResolver.resolve(card)).willReturn("리자몽");
        given(watchlistRepository.markListingNotifiedIfNotYet(10L)).willReturn(1);

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        assertThat(w1.isListingNotified()).isTrue();
        then(notificationService).should().createListingAvailableNotification(w1, "리자몽", card);
    }

    @Test
    @DisplayName("한글명 매핑이 없으면 카드 원본 이름으로 폴백한다")
    void onListingCreated_fallsBackToOriginalNameWhenNoKoreanName() {
        Watchlist w1 = watchlist(10L, 1L);
        Card card = Card.builder().id(1L).name("Charizard").build();
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(1L);
        given(watchlistRepository.findByCardIdAndListingNotifiedFalse(1L)).willReturn(List.of(w1));
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardNameKoResolver.resolve(card)).willReturn(null);
        given(watchlistRepository.markListingNotifiedIfNotYet(10L)).willReturn(1);

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        then(notificationService).should().createListingAvailableNotification(w1, "Charizard", card);
    }

    @Test
    @DisplayName("이미 다른 트랜잭션이 선점(claim 실패)한 워치리스트는 알림을 만들지 않는다")
    void onListingCreated_skipsWhenClaimFails() {
        Watchlist w1 = watchlist(10L, 1L);
        Card card = Card.builder().id(1L).name("Charizard").build();
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(1L);
        given(watchlistRepository.findByCardIdAndListingNotifiedFalse(1L)).willReturn(List.of(w1));
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardNameKoResolver.resolve(card)).willReturn("리자몽");
        given(watchlistRepository.markListingNotifiedIfNotYet(10L)).willReturn(0);

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        assertThat(w1.isListingNotified()).isFalse();
        then(notificationService).should(never()).createListingAvailableNotification(any(), any(), any());
    }

    @Test
    @DisplayName("여러 워치리스트 등록자 중 일부만 claim에 성공하면 성공한 대상에게만 알림을 보낸다")
    void onListingCreated_notifiesOnlyClaimedWatchers() {
        Watchlist w1 = watchlist(10L, 1L);
        Watchlist w2 = watchlist(20L, 1L);
        Card card = Card.builder().id(1L).name("Charizard").build();
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(1L);
        given(watchlistRepository.findByCardIdAndListingNotifiedFalse(1L)).willReturn(List.of(w1, w2));
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(cardNameKoResolver.resolve(card)).willReturn("리자몽");
        given(watchlistRepository.markListingNotifiedIfNotYet(10L)).willReturn(1);
        given(watchlistRepository.markListingNotifiedIfNotYet(20L)).willReturn(0);

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        then(notificationService).should().createListingAvailableNotification(eq(w1), any(), any());
        then(notificationService).should(never()).createListingAvailableNotification(eq(w2), any(), any());
    }

    @Test
    @DisplayName("카드를 찾을 수 없으면 알림을 만들지 않는다")
    void onListingCreated_skipsWhenCardNotFound() {
        Watchlist w1 = watchlist(10L, 1L);
        given(listingRepository.countByCardIdAndStatus(1L, ListingStatus.ACTIVE)).willReturn(1L);
        given(watchlistRepository.findByCardIdAndListingNotifiedFalse(1L)).willReturn(List.of(w1));
        given(cardRepository.findById(1L)).willReturn(Optional.empty());

        listener.onListingCreated(new ListingCreatedEvent(1L, null));

        then(watchlistRepository).should(never()).markListingNotifiedIfNotYet(any());
        then(notificationService).should(never()).createListingAvailableNotification(any(), any(), any());
    }
}
