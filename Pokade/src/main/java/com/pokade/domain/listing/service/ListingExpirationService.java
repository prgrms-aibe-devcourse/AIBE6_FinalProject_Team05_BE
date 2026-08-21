package com.pokade.domain.listing.service;

import com.pokade.domain.listing.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingExpirationService {

    // 등록 후 이 기간이 지나도록 판매되지 않은 ACTIVE 매물을 자동 만료 처리한다.
    // ListingStaleNoticeService의 30일 미체결 알림 이후에도 30일의 유예를 더 주는 값(총 60일)으로,
    // 아직 팀 합의된 정책이 없어 임시로 정한 기준이다 — 정책이 정해지면 이 상수만 교체하면 된다.
    private static final int EXPIRE_DAYS = 60;

    private final ListingRepository listingRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void expireStaleListings() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(EXPIRE_DAYS);
        int expiredCount = listingRepository.expireActiveListingsCreatedBefore(cutoff);
        if (expiredCount > 0) {
            log.info("기간 만료 처리된 매물 수: {}", expiredCount);
        }
    }
}
