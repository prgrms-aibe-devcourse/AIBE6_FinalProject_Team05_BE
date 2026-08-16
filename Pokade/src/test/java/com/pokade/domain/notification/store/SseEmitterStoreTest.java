package com.pokade.domain.notification.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterStoreTest {

    private final SseEmitterStore store = new SseEmitterStore();

    @Test
    @DisplayName("save: 등록한 Emitter를 findByUserId로 조회할 수 있다")
    void save_and_find() {
        SseEmitter emitter = new SseEmitter();

        store.save(1L, emitter);

        assertThat(store.findByUserId(1L)).containsExactly(emitter);
    }

    @Test
    @DisplayName("findByUserId: 등록된 Emitter가 없으면 빈 리스트를 반환한다")
    void find_empty() {
        assertThat(store.findByUserId(999L)).isEmpty();
    }

    @Test
    @DisplayName("save: 같은 유저가 여러 번(다른 탭/기기) 구독하면 모두 유지된다")
    void save_multiple_for_same_user() {
        SseEmitter first = new SseEmitter();
        SseEmitter second = new SseEmitter();

        store.save(1L, first);
        store.save(1L, second);

        assertThat(store.findByUserId(1L)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("remove: 여러 Emitter 중 하나만 제거해도 나머지는 유지된다")
    void remove_one_keeps_others() {
        SseEmitter first = new SseEmitter();
        SseEmitter second = new SseEmitter();
        store.save(1L, first);
        store.save(1L, second);

        store.remove(1L, first);

        assertThat(store.findByUserId(1L)).containsExactly(second);
    }

    @Test
    @DisplayName("remove: 마지막 Emitter까지 제거하면 findByUserId는 빈 리스트를 반환한다")
    void remove_last_leaves_empty() {
        SseEmitter emitter = new SseEmitter();
        store.save(1L, emitter);

        store.remove(1L, emitter);

        assertThat(store.findByUserId(1L)).isEmpty();
    }

    @Test
    @DisplayName("remove: 등록되지 않은 Emitter를 제거해도 예외 없이 무시된다")
    void remove_unknown_is_noop() {
        store.remove(1L, new SseEmitter());

        assertThat(store.findByUserId(1L)).isEmpty();
    }

    @Test
    @DisplayName("findAll: 등록된 유저가 없으면 빈 리스트를 반환한다")
    void findAll_empty() {
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    @DisplayName("findAll: 여러 유저에 등록된 Emitter를 모두 반환한다")
    void findAll_returns_every_user_emitter() {
        SseEmitter userOneFirst = new SseEmitter();
        SseEmitter userOneSecond = new SseEmitter();
        SseEmitter userTwo = new SseEmitter();
        store.save(1L, userOneFirst);
        store.save(1L, userOneSecond);
        store.save(2L, userTwo);

        assertThat(store.findAll()).containsExactlyInAnyOrder(userOneFirst, userOneSecond, userTwo);
    }
}
