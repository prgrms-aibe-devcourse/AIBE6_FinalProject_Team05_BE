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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동기 발송 vs 비동기 발송의 "호출자 대기 시간" 비교 데모.
 * 실제 메일은 보내지 않고, 발송 지연을 sleep(1초)으로 흉내 낸다.
 * - 동기 경로: 호출자가 발송이 끝날 때까지(약 1초) 붙잡힌다.
 * - 비동기 경로: 호출자는 즉시(수 ms) 반환되고, 발송은 백그라운드에서 나중에 끝난다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SyncVsAsyncWaitDemoTest.DemoConfig.class)
class SyncVsAsyncWaitDemoTest {

    @EnableAsync
    @Configuration
    static class DemoConfig {
        @Bean
        SlowSender slowSender() {
            return new SlowSender();
        }

        @Bean
        MailFacade mailFacade(SlowSender slowSender) {
            return new MailFacade(slowSender);
        }
    }

    // SMTP 발송을 흉내 내는 느린 발송기 (프록시 대상 아님 → 필드 직접 읽기 안전)
    static class SlowSender {
        static final long DELAY_MS = 1000;
        final AtomicInteger completed = new AtomicInteger();

        void send() {
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completed.incrementAndGet();
        }
    }

    static class MailFacade {
        private final SlowSender sender;

        MailFacade(SlowSender sender) {
            this.sender = sender;
        }

        // 동기: 발송이 끝날 때까지 호출자가 대기
        public void sendSync() {
            sender.send();
        }

        // 비동기: 호출자는 즉시 반환, 발송은 백그라운드
        @Async
        public void sendAsync() {
            sender.send();
        }
    }

    @Autowired
    MailFacade mailFacade;
    @Autowired
    SlowSender slowSender;

    @Test
    void 동기_발송은_호출자를_발송_끝날_때까지_붙잡는다() {
        slowSender.completed.set(0);

        long start = System.nanoTime();
        mailFacade.sendSync();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[sync]  호출자 대기 = " + elapsedMs + "ms / 반환 시점 발송완료수 = " + slowSender.completed.get());
        assertThat(elapsedMs).isGreaterThanOrEqualTo(900);   // 발송 시간만큼 대기
        assertThat(slowSender.completed.get()).isEqualTo(1); // 반환 시점에 이미 발송 완료
    }

    @Test
    void 비동기_발송은_호출자를_즉시_돌려주고_발송은_뒤에서_끝난다() throws InterruptedException {
        slowSender.completed.set(0);

        long start = System.nanoTime();
        mailFacade.sendAsync();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        int completedRightAfterReturn = slowSender.completed.get();

        System.out.println("[async] 호출자 대기 = " + elapsedMs + "ms / 반환 직후 발송완료수 = " + completedRightAfterReturn);
        assertThat(elapsedMs).isLessThan(100);               // 즉시 반환
        assertThat(completedRightAfterReturn).isZero();      // 반환 시점엔 아직 발송 안 끝남

        Thread.sleep(SlowSender.DELAY_MS + 500);             // 백그라운드 완료 대기
        System.out.println("[async] 잠시 후 발송완료수 = " + slowSender.completed.get());
        assertThat(slowSender.completed.get()).isEqualTo(1); // 발송은 백그라운드에서 실제로 수행됨
    }
}
