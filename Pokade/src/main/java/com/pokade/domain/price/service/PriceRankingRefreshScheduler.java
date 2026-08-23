package com.pokade.domain.price.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 시세 랭킹(GET /api/prices/ranking)을 매 요청마다 전체 카드를 스캔해서 계산하면 운영 규모(카드 2만+)에서
// 느려진다 - 하루 한 번 이 스케줄러가 미리 계산해서 캐시(PriceService.RANKING_CACHE)에 채워두고,
// 실제 요청은 PriceService.getRanking()의 캐시 조회만 탄다(사용자 요청, 2026-08-24). 하루 한 번으로도
// 충분한 이유: 랭킹 자체가 "최근 7일 vs 이전 7일 블록 평균가" 비교라 하루 이내의 변동은 지표에 거의
// 반영되지 않는다(사용자 판단, 2026-08-24) - 10분 주기였던 최초 설계보다 배치 비용을 크게 줄인다.
// rise/fall 중 하나가 실패해도 다른 하나는 계속 갱신되도록 각각 개별 try/catch로 감싼다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceRankingRefreshScheduler {

    private static final List<String> RANKING_TYPES = List.of("rise", "fall");

    private final PriceService priceService;

    // 매일 새벽 4시(서버 기본 타임존 기준)에 1회 실행 - 트래픽이 가장 적은 시간대를 골라 배치 부하가
    // 실사용 요청과 겹치지 않게 한다(사용자 요청, 2026-08-24).
    @Scheduled(cron = "0 0 4 * * *")
    public void refreshRankings() {
        for (String type : RANKING_TYPES) {
            try {
                priceService.refreshRanking(type);
            } catch (Exception e) {
                log.warn("시세 랭킹 배치 갱신 실패: type={}", type, e);
            }
        }
    }
}
