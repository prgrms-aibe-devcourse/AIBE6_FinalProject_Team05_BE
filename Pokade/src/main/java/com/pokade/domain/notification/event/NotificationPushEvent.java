package com.pokade.domain.notification.event;

import com.pokade.domain.notification.dto.NotificationResponse;

// 알림 저장 트랜잭션이 실제로 커밋된 뒤에만 SSE로 밀어주기 위한 이벤트.
public record NotificationPushEvent(Long userId, NotificationResponse response) {
}
