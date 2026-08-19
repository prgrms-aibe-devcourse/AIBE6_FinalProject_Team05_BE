package com.pokade.domain.notification.service;

import com.pokade.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 알림 자동 정리 배치(#162, 하이브리드 TTL). 읽은 알림은 READ_RETENTION_DAYS, 안 읽은 알림도
// UNREAD_RETENTION_DAYS가 지나면 삭제해 notifications 테이블이 무한 누적되지 않게 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    private static final int READ_RETENTION_DAYS = 30;
    private static final int UNREAD_RETENTION_DAYS = 180;

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 5 * * MON")
    @Transactional
    public void deleteExpiredNotifications() {
        LocalDateTime readCutoff = LocalDateTime.now().minusDays(READ_RETENTION_DAYS);
        LocalDateTime unreadCutoff = LocalDateTime.now().minusDays(UNREAD_RETENTION_DAYS);

        int deleted = notificationRepository.deleteExpiredNotifications(readCutoff, unreadCutoff);
        log.info("만료된 알림 {}건을 정리했습니다.", deleted);
    }
}
