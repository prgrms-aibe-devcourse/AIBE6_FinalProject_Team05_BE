package com.pokade.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Async self-invocation 제약 증명용 데모.
 * - 같은 클래스 안에서 자기 @Async 메서드를 부르면(self-invocation) 프록시를 안 타 동기(같은 스레드)로 실행된다.
 * - 주입받은 빈(프록시)으로 호출하면 다른 스레드에서 비동기로 실행된다.
 *
 * 스레드 이름은 별도 수집 빈(Recorder)에 기록한다. (CGLIB 프록시의 필드를 직접 읽으면 타깃과 달라 부정확)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SelfInvocationAsyncDemoTest.DemoConfig.class)
class SelfInvocationAsyncDemoTest {

    @EnableAsync
    @Configuration
    static class DemoConfig {
        @Bean
        Recorder recorder() {
            return new Recorder();
        }

        @Bean
        AsyncDemo asyncDemo(Recorder recorder) {
            return new AsyncDemo(recorder);
        }
    }

    static class Recorder {
        volatile String selfThread;
        volatile String proxyThread;
        CountDownLatch proxyLatch = new CountDownLatch(1);
    }

    static class AsyncDemo {
        private final Recorder recorder;

        AsyncDemo(Recorder recorder) {
            this.recorder = recorder;
        }

        // 서비스 내부에서 자기 @Async 메서드를 부르는 상황 (self-invocation)
        public void callSelf() {
            asyncWork("self");
        }

        @Async
        public void asyncWork(String mode) {
            if ("self".equals(mode)) {
                recorder.selfThread = Thread.currentThread().getName();
            } else {
                recorder.proxyThread = Thread.currentThread().getName();
                recorder.proxyLatch.countDown();
            }
        }
    }

    @Autowired
    AsyncDemo demo;
    @Autowired
    Recorder recorder;

    @Test
    void self_invocation은_같은_스레드에서_동기로_실행된다() {
        String caller = Thread.currentThread().getName();

        demo.callSelf(); // self-invocation: 프록시를 타지 않음

        System.out.println("[self-invocation] caller=" + caller + " / worker=" + recorder.selfThread);
        assertThat(recorder.selfThread).isEqualTo(caller); // 같은 스레드 = 비동기 안 걸림
    }

    @Test
    void 프록시로_호출하면_다른_스레드에서_비동기로_실행된다() throws InterruptedException {
        String caller = Thread.currentThread().getName();

        demo.asyncWork("proxy"); // 주입받은 프록시 빈으로 직접 호출

        recorder.proxyLatch.await(2, TimeUnit.SECONDS);
        System.out.println("[proxy call] caller=" + caller + " / worker=" + recorder.proxyThread);
        assertThat(recorder.proxyThread).isNotEqualTo(caller); // 다른 스레드 = 비동기 걸림
    }
}
