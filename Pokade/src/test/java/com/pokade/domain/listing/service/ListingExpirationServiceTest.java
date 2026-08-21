package com.pokade.domain.listing.service;

import com.pokade.domain.listing.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListingExpirationServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingExpirationService listingExpirationService;

    @Test
    void 등록후_60일_이전_cutoff로_만료_대상을_일괄_처리한다() {
        given(listingRepository.expireActiveListingsCreatedBefore(any())).willReturn(3);

        listingExpirationService.expireStaleListings();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(listingRepository).expireActiveListingsCreatedBefore(cutoffCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(60);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 만료_대상이_없으면_0건_처리된다() {
        given(listingRepository.expireActiveListingsCreatedBefore(any())).willReturn(0);

        listingExpirationService.expireStaleListings();

        verify(listingRepository).expireActiveListingsCreatedBefore(any());
    }
}
