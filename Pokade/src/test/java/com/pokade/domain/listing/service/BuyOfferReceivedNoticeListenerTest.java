package com.pokade.domain.listing.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.global.event.BuyOfferCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BuyOfferReceivedNoticeListenerTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardNameKoResolver cardNameKoResolver;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BuyOfferReceivedNoticeListener listener;

    private static final Long CARD_ID = 55L;
    private static final Long VARIANT_ID = 7L;
    private static final Long BUYER_ID = 200L;

    private BuyOfferCreatedEvent event() {
        return event(ListingGrade.S);
    }

    private BuyOfferCreatedEvent event(ListingGrade grade) {
        return new BuyOfferCreatedEvent(1L, CARD_ID, VARIANT_ID, grade, BUYER_ID, 150000);
    }

    private Listing listing(Long sellerId) {
        return Listing.builder()
                .cardId(CARD_ID).sellerId(sellerId).variantId(VARIANT_ID)
                .price(150000).grade(ListingGrade.S)
                .build();
    }

    private Card card() {
        return Card.builder().id(CARD_ID).name("Charizard ex").imageMedium("medium.png").build();
    }

    private void givenListings(List<Listing> listings) {
        given(listingRepository.findOrderbook(eq(CARD_ID), eq(VARIANT_ID), eq(ListingStatus.ACTIVE), any()))
                .willReturn(listings);
    }

    @SuppressWarnings("unchecked")
    private List<Long> captureSellerIds() {
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        then(notificationService).should()
                .createBuyOfferReceivedNotification(captor.capture(), eq(CARD_ID), any(), eq(150000), any());
        return captor.getValue();
    }

    @Test
    @DisplayName("한 판매자가 같은 카드에 매물을 여러 개 갖고 있어도 알림 대상에는 한 번만 들어간다")
    void dedupesSellerWithMultipleListings() {
        givenListings(List.of(listing(10L), listing(10L), listing(11L)));
        given(cardRepository.findById(CARD_ID)).willReturn(Optional.of(card()));
        given(cardNameKoResolver.resolve(any(Card.class))).willReturn("리자몽 ex");

        listener.onBuyOfferCreated(event());

        assertThat(captureSellerIds()).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("입찰자 본인이 올린 매물은 알림 대상에서 빠진다")
    void excludesBidderOwnListing() {
        givenListings(List.of(listing(BUYER_ID), listing(11L)));
        given(cardRepository.findById(CARD_ID)).willReturn(Optional.of(card()));
        given(cardNameKoResolver.resolve(any(Card.class))).willReturn("리자몽 ex");

        listener.onBuyOfferCreated(event());

        assertThat(captureSellerIds()).containsExactly(11L);
    }

    @Test
    @DisplayName("알릴 판매자가 하나도 없으면 카드 조회도 알림 생성도 하지 않는다")
    void withNoSeller_doesNothing() {
        // 매물이 전부 입찰자 본인 것이면 걸러진 뒤 비게 된다 - 매물 0건과 같은 경로다.
        givenListings(List.of(listing(BUYER_ID)));

        listener.onBuyOfferCreated(event());

        then(cardRepository).should(never()).findById(any());
        then(notificationService).should(never())
                .createBuyOfferReceivedNotification(anyList(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("카드를 찾을 수 없으면 예외 없이 스킵하고 알림을 만들지 않는다")
    void withMissingCard_skipsQuietly() {
        givenListings(List.of(listing(10L)));
        given(cardRepository.findById(CARD_ID)).willReturn(Optional.empty());

        listener.onBuyOfferCreated(event());

        then(notificationService).should(never())
                .createBuyOfferReceivedNotification(anyList(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("등급 무관 입찰(grade=null)이면 조회에도 null을 그대로 넘겨 전 등급 판매자를 대상으로 삼는다")
    void withNullGrade_queriesEveryGrade() {
        given(listingRepository.findOrderbook(eq(CARD_ID), eq(VARIANT_ID), eq(ListingStatus.ACTIVE), isNull()))
                .willReturn(List.of(listing(10L)));
        given(cardRepository.findById(CARD_ID)).willReturn(Optional.of(card()));
        given(cardNameKoResolver.resolve(any(Card.class))).willReturn("리자몽 ex");

        listener.onBuyOfferCreated(event(null));

        then(listingRepository).should()
                .findOrderbook(eq(CARD_ID), eq(VARIANT_ID), eq(ListingStatus.ACTIVE), isNull());
        assertThat(captureSellerIds()).containsExactly(10L);
    }
}
