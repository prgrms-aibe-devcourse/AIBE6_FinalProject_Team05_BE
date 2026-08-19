package com.pokade.domain.notification.service;

import com.pokade.domain.notification.repository.NotificationRepository;
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
class NotificationCleanupServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationCleanupService notificationCleanupService;

    @Test
    void 읽은_알림은_30일_안읽은_알림은_180일_cutoff로_정리를_요청한다() {
        given(notificationRepository.deleteExpiredNotifications(any(), any())).willReturn(0);

        notificationCleanupService.deleteExpiredNotifications();

        ArgumentCaptor<LocalDateTime> readCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> unreadCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository)
                .deleteExpiredNotifications(readCutoffCaptor.capture(), unreadCutoffCaptor.capture());

        assertThat(readCutoffCaptor.getValue())
                .isCloseTo(LocalDateTime.now().minusDays(30), within(1, ChronoUnit.MINUTES));
        assertThat(unreadCutoffCaptor.getValue())
                .isCloseTo(LocalDateTime.now().minusDays(180), within(1, ChronoUnit.MINUTES));
    }
}
