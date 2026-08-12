package com.pokade.domain.trade.service;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.event.UserWithdrawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalCleanupListenerTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private UserWithdrawalCleanupListener listener;

    private Listing activeListingOf(Long sellerId) {
        return Listing.builder()
                .cardId(1L)
                .sellerId(sellerId)
                .price(10000)
                .build();
    }

    private Trade pendingTradeOf(Long sellerId, Long buyerId) {
        Listing listing = activeListingOf(sellerId);
        return Trade.builder()
                .listing(listing)
                .buyerId(buyerId)
                .price(10000)
                .build();
    }

    @Test
    void 탈퇴한_유저의_ACTIVE_매물이_전부_취소된다() {
        Long userId = 100L;
        Listing listing1 = activeListingOf(userId);
        Listing listing2 = activeListingOf(userId);
        given(listingRepository.findBySellerIdAndStatus(userId, ListingStatus.ACTIVE))
                .willReturn(List.of(listing1, listing2));
        given(tradeRepository.findByParticipantIdAndStatusIn(eq(userId), eq(List.of(TradeStatus.PENDING, TradeStatus.SHIPPED_TO_PLATFORM, TradeStatus.INSPECTED, TradeStatus.DELIVERED))))
                .willReturn(List.of());

        listener.onUserWithdrawn(new UserWithdrawnEvent(userId));

        assertThat(listing1.getStatus()).isEqualTo(ListingStatus.CANCELLED);
        assertThat(listing2.getStatus()).isEqualTo(ListingStatus.CANCELLED);
    }

    @Test
    void 탈퇴한_유저가_참여한_미종결_거래가_전부_취소된다() {
        Long userId = 200L;
        Trade tradeAsBuyer = pendingTradeOf(100L, userId);
        given(listingRepository.findBySellerIdAndStatus(userId, ListingStatus.ACTIVE))
                .willReturn(List.of());
        given(tradeRepository.findByParticipantIdAndStatusIn(eq(userId), eq(List.of(TradeStatus.PENDING, TradeStatus.SHIPPED_TO_PLATFORM, TradeStatus.INSPECTED, TradeStatus.DELIVERED))))
                .willReturn(List.of(tradeAsBuyer));

        listener.onUserWithdrawn(new UserWithdrawnEvent(userId));

        assertThat(tradeAsBuyer.getStatus()).isEqualTo(TradeStatus.CANCELLED);
    }
}
