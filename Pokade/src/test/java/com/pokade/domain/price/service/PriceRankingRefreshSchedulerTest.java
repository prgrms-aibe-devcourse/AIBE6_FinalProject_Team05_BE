package com.pokade.domain.price.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PriceRankingRefreshSchedulerTest {

    @Mock
    private PriceService priceService;

    @InjectMocks
    private PriceRankingRefreshScheduler scheduler;

    @Test
    @DisplayName("rise/fall 둘 다 refreshRanking()으로 위임해 캐시를 갱신한다")
    void refresh_delegatesBothTypesToPriceService() {
        scheduler.refreshRankings();

        then(priceService).should().refreshRanking("rise");
        then(priceService).should().refreshRanking("fall");
    }

    @Test
    @DisplayName("한쪽 타입 갱신이 예외를 던져도 다른 타입은 정상적으로 갱신된다")
    void refresh_oneTypeFails_otherStillRefreshed() {
        doThrow(new RuntimeException("일시적 DB 오류")).when(priceService).refreshRanking("rise");
        given(priceService.refreshRanking("fall")).willReturn(List.of());

        scheduler.refreshRankings();

        then(priceService).should().refreshRanking("rise");
        then(priceService).should().refreshRanking("fall");
    }
}
