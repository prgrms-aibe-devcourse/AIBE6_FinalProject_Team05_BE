package com.pokade.domain.notification.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    private Notification notification() {
        return Notification.builder()
                .userId(1L).type(NotificationType.PRICE_TARGET).message("메시지").cardId(10L)
                .build();
    }

    @Test
    @DisplayName("of: card가 null이면(카드와 무관한 알림) cardImageUrl도 null이다")
    void of_nullCard_cardImageUrlNull() {
        NotificationResponse response = NotificationResponse.of(notification(), null);

        assertThat(response.cardImageUrl()).isNull();
    }

    @Test
    @DisplayName("of: imageMedium이 있으면 imageMedium을 cardImageUrl로 쓴다")
    void of_prefersImageMedium() {
        Card card = Card.builder().id(10L).name("리자몽").imageSmall("small.png").imageMedium("medium.png").build();

        NotificationResponse response = NotificationResponse.of(notification(), card);

        assertThat(response.cardImageUrl()).isEqualTo("medium.png");
    }

    @Test
    @DisplayName("of: imageMedium이 없으면 imageSmall로 폴백한다")
    void of_fallsBackToImageSmall() {
        Card card = Card.builder().id(10L).name("리자몽").imageSmall("small.png").build();

        NotificationResponse response = NotificationResponse.of(notification(), card);

        assertThat(response.cardImageUrl()).isEqualTo("small.png");
    }
}
