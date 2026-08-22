package com.pokade.domain.notification.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String message,
        Long cardId,
        String cardImageUrl,
        Long inquiryId,
        boolean isRead,
        LocalDateTime createdAt
) {

    // card는 알림 생성/조회 시점에 cardId로 조회한 카드(없으면 null) - 알림 자체엔 이미지를 스냅샷으로
    // 저장하지 않고 항상 그 시점의 최신 카드 이미지를 반영한다(카드가 무관한 알림은 card=null로 넘긴다).
    public static NotificationResponse of(Notification notification, Card card) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getCardId(),
                resolveImageUrl(card),
                notification.getInquiryId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    // WatchlistResponse.resolveImageUrl()과 동일한 컨벤션 - imageMedium 우선, 없으면 imageSmall로 폴백.
    private static String resolveImageUrl(Card card) {
        if (card == null) {
            return null;
        }
        return card.getImageMedium() != null ? card.getImageMedium() : card.getImageSmall();
    }
}
