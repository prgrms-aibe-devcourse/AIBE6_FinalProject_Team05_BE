package com.pokade.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SpringBoot가 기본 스레드 풀(applicationTaskExecutor)자동으로 띄워줌
 *
 * @Async는 그걸 알아서 사용함 현재 규모에서는 커스텀 풀 필요 없이
 * 최소한으로 진행 나중에 튜닝 필요하면 그때 executor 빈 추가
 */
@Configuration
@EnableAsync
public class AsyncConfig {

}
