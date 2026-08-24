package com.pokade.domain.card.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokade.domain.card.repository.CardRepository;

// 실제 리셋 쿼리의 동작(일간만 0으로, 누적은 유지, 조건부 갱신 건수)은 CardRepositoryTest t61에서
// DB로 검증한다 - 이 클래스는 스케줄러가 그 쿼리에 위임하고 누적 카운터를 건드리지 않는지만 본다.
@ExtendWith(MockitoExtension.class)
class DailyViewCountResetSchedulerTest {

    @Mock CardRepository cardRepository;
    @InjectMocks DailyViewCountResetScheduler scheduler;

    @Test
    @DisplayName("리셋 대상이 있으면 resetDailyViewCounts()에 위임하고 누적 조회수는 건드리지 않는다")
    void reset_delegatesToRepository() {
        given(cardRepository.resetDailyViewCounts()).willReturn(3);

        scheduler.resetDailyViewCounts();

        then(cardRepository).should().resetDailyViewCounts();
        then(cardRepository).should(never()).incrementViewCounts(any());
    }

    @Test
    @DisplayName("리셋 대상이 0건이어도 예외 없이 정상 종료된다")
    void reset_noRows_doesNotThrow() {
        given(cardRepository.resetDailyViewCounts()).willReturn(0);

        scheduler.resetDailyViewCounts();

        then(cardRepository).should().resetDailyViewCounts();
    }
}
