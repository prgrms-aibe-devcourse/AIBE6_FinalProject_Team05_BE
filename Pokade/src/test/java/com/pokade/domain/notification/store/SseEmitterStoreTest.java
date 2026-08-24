package com.pokade.domain.notification.store;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseEmitterStoreTest {

    private final SseEmitterStore store = new SseEmitterStore(new SimpleMeterRegistry());

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

    // #386: 새로고침(F5) 시나리오 - 브라우저가 기존 SSE 연결을 끊으면서(onCompletion → remove) 새 연결을
    // 여는(subscribe → save) 두 호출이 겹친다. 그 유저의 마지막 연결이 닫히는 순간이면 remove가 맵
    // 엔트리를 삭제하는데, save가 맵 잠금 밖에서 리스트에 추가하면 새 Emitter가 맵에서 떨어진 리스트에
    // 담겨 알림·하트비트를 영구히 못 받는다(로그의 재조회에서 NPE가 나기도 한다).
    //
    // 인터리빙 순서 자체는 스케줄러에 달려 있어 통제하지 않는다. 대신 "순서와 무관하게 항상 성립해야
    // 하는 성질"만 단정한다 - 어느 쪽이 먼저 실행되든 방금 등록한 새 Emitter는 반드시 조회돼야 한다.
    // 그래서 단정은 결정론적이고, 라운드를 반복하는 것은 두 순서를 모두 밟게 하기 위한 것일 뿐이다.
    // sleep으로 순서를 유도하지 않고 CyclicBarrier로 두 스레드를 같은 시점에 출발시킨다.
    @Test
    @DisplayName("remove와 save가 동시에 일어나도 새로 등록한 Emitter는 유실되지 않는다(#386)")
    void save_concurrentWithRemove_keepsNewEmitter() throws Exception {
        int rounds = 500;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                // 라운드마다 다른 userId를 써서 이전 라운드의 잔여 상태가 다음 판정에 섞이지 않게 한다.
                long userId = round;
                SseEmitter closing = new SseEmitter();
                SseEmitter opening = new SseEmitter();
                // 이미 열려 있던 연결 하나 - 이게 마지막 연결이라 remove가 맵 엔트리를 지우게 된다.
                store.save(userId, closing);

                CyclicBarrier startLine = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                futures.add(executor.submit(() -> {
                    startLine.await();
                    store.remove(userId, closing);
                    return null;
                }));
                futures.add(executor.submit(() -> {
                    startLine.await();
                    store.save(userId, opening);
                    return null;
                }));

                // future.get()으로 각 스레드의 예외를 즉시 드러낸다 - submit만 하고 get을 부르지 않으면
                // save() 안의 NPE가 조용히 삼켜져 "유실"만 보이고 원인을 놓친다.
                for (Future<?> future : futures) {
                    assertThatCode(future::get)
                            .as("round=%d - save/remove 동시 실행이 예외 없이 끝나야 한다", round)
                            .doesNotThrowAnyException();
                }

                assertThat(store.findByUserId(userId))
                        .as("round=%d - 새로 등록한 Emitter는 remove와 겹쳐도 남아 있어야 한다", round)
                        .contains(opening);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("테스트 스레드풀이 타임아웃 전에 종료되어야 한다")
                    .isTrue();
        }
    }
}
