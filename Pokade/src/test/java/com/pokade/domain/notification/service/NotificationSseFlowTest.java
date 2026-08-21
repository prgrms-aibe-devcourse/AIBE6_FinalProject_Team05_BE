package com.pokade.domain.notification.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.notification.repository.NotificationRepository;
import com.pokade.domain.notification.store.SseEmitterStore;
import com.pokade.domain.watchlist.entity.Watchlist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

// NotificationServiceTest는 SseEmitterStore를 mock으로 대체해 sendEvent() 호출 여부만 검증한다.
// 이 클래스는 실제 서버(로컬 curl 검증, #230 PR 리뷰 대응)에서 일어나는 순서 그대로 -
// subscribe()로 등록한 실제 Emitter가 실제 SseEmitterStore에 저장된 뒤, createPriceTargetNotification()이
// "같은 userId 키"로 그 Emitter를 정확히 찾아 예외 없이 전송하는지를 mock 없이(NotificationRepository만 제외)
// 확인한다. 실제 TCP 소켓이 없어 전송된 바이트 자체는 검증할 수 없지만, subscribe→push 사이의 실제 객체
// 배선(같은 store, 같은 userId 키)이 올바른지는 이 테스트가 유닛 테스트보다 더 신뢰성 있게 보여준다.
@ExtendWith(MockitoExtension.class)
class NotificationSseFlowTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CardRepository cardRepository;

    private final SseEmitterStore sseEmitterStore = new SseEmitterStore();
    private NotificationService notificationService;

    private Card card() {
        return Card.builder().id(10L).name("리자몽").build();
    }

    @Test
    @DisplayName("subscribe 후 같은 유저에게 createPriceTargetNotification이 발생하면 연결이 끊기지 않고 유지된다")
    void subscribe_then_notify_keeps_connection_alive() {
        notificationService = new NotificationService(notificationRepository, cardRepository, sseEmitterStore, event -> { });
        Long userId = 1L;
        Watchlist watchlist = Watchlist.builder().userId(userId).cardId(10L).targetBuyPrice(100000).build();

        SseEmitter emitter = notificationService.subscribe(userId);
        assertThat(sseEmitterStore.findByUserId(userId)).containsExactly(emitter);

        assertThatCode(() ->
                notificationService.createPriceTargetNotification(watchlist, "리자몽", card(), 100000))
                .doesNotThrowAnyException();

        // 전송이 실패했다면 sendEvent()의 completeWithError -> onError 콜백으로 store에서 제거됐을 것이다.
        // 여전히 남아있다는 것은 실제 Emitter 객체에 대한 send()가 예외 없이 끝났다는 뜻이다.
        assertThat(sseEmitterStore.findByUserId(userId)).containsExactly(emitter);
    }

    @Test
    @DisplayName("다른 유저를 구독 중인 Emitter는 대상 유저에게 온 알림의 영향을 받지 않는다")
    void notification_for_one_user_does_not_touch_another_subscriber() {
        notificationService = new NotificationService(notificationRepository, cardRepository, sseEmitterStore, event -> { });
        Long targetUserId = 1L;
        Long otherUserId = 2L;
        Watchlist watchlist = Watchlist.builder().userId(targetUserId).cardId(10L).targetBuyPrice(100000).build();

        SseEmitter otherEmitter = notificationService.subscribe(otherUserId);
        notificationService.subscribe(targetUserId);

        notificationService.createPriceTargetNotification(watchlist, "리자몽", card(), 100000);

        assertThat(sseEmitterStore.findByUserId(otherUserId)).containsExactly(otherEmitter);
    }
}
