package com.pokade.domain.card.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pokade.domain.card.repository.CardRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인기순 정렬(sort=popular)의 기준을 하루 단위로 고정하기 위해, 매일 자정에 cards.daily_view_count를
// 0으로 되돌린다(#377). 카드 상세의 "N회 조회"에 쓰는 누적 view_count는 건드리지 않는다 - 두 값을
// 분리한 이유가 "누적은 그대로 보여주고 랭킹만 하루 단위로 끊는다"는 것이기 때문이다.
//
// 자정을 고른 이유: "오늘의 인기 카드"가 달력상 하루와 어긋나지 않게 하려면 경계가 00:00이어야 한다.
// 트래픽이 적은 새벽 시간대로 미루면 그만큼 전날 조회수가 오늘 랭킹에 섞인다. 리셋은 조건부 단일
// UPDATE라 비용이 거의 없어서 트래픽 시간대를 피할 필요가 없다.
//
// 실패 시 try/catch로 감싸지 않는 이유: 쿼리 1개짜리 전량 처리라 다른 스케줄러들처럼 "일부만 실패해도
// 나머지는 진행"시킬 대상이 없다. 실패하면 Spring 스케줄러가 스택트레이스를 남기고, 그날 하루 랭킹이
// 전날 값을 이어받은 상태로 유지된다(다음 자정에 자동 복구).
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyViewCountResetScheduler {

    private final CardRepository cardRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyViewCounts() {
        int resetCount = cardRepository.resetDailyViewCounts();
        if (resetCount > 0) {
            log.info("일간 조회수 리셋된 카드 수: {}", resetCount);
        }
    }
}
