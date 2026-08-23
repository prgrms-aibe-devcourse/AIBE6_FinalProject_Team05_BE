package com.pokade.global.config;

import java.time.Duration;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// CachingConfigurer를 구현하는 이유: 느슨한 CacheErrorHandler 빈만 등록하면 @EnableCaching이
// CachingConfigurer가 없을 때 자동으로 그 빈을 집어가지 않는다(스프링 캐시 프록시 설정은
// CachingConfigurer 존재 여부만 확인함) - errorHandler()를 명시적으로 오버라이드해야 실제로 적용된다.
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

    private static final String CARD_FACETS_CACHE = "cardFacets";
    // PriceService.getRanking()/refreshRanking() - PriceRankingRefreshScheduler가 매일 자정 갱신하므로
    // TTL은 하루(24h)보다 여유 있게 잡아서, 스케줄러가 하루 실패해도 다음 날엔 결국 새로 계산되게 한다.
    private static final String PRICE_RANKING_CACHE = "priceRanking";

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    @Bean
    public CacheManager cacheManager() {
        return buildRedisCacheManager(redisConnectionFactory);
    }

    // GenericJackson2JsonRedisSerializer는 Spring Data Redis 4.0부터 제거 예정(forRemoval=true)으로
    // 표시돼 있다. 대체 클래스(GenericJacksonJsonRedisSerializer)는 Jackson 3(tools.jackson) 기반이라,
    // 프로젝트 전체가 아직 Jackson 2(com.fasterxml.jackson)를 쓰는 현재 상태와 호환되지 않는다 -
    // Jackson 3 마이그레이션은 이 작업(facets 캐싱) 범위를 벗어나므로 지금은 경고만 억제하고 기존
    // 직렬화기를 사용한다. TODO: 실제로 이 클래스가 제거되는 Spring Data Redis 버전으로 올릴 때는
    // 프로젝트가 Jackson 3로 이관된 뒤 GenericJacksonJsonRedisSerializer로 마이그레이션해야 한다.
    @SuppressWarnings("removal")
    private RedisCacheManager buildRedisCacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 직렬화(JdkSerializationRedisSerializer)는 값 타입이 Serializable이어야 하는데,
        // CardFacetsResponse 등 응답 DTO는 일반 record라 그대로 쓰면 NotSerializableException이 난다.
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJackson2JsonRedisSerializer.builder().build()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                // 세트/타입/레어도 데이터는 Scrydex 배치 동기화(보통 일 단위) 때만 바뀌므로 1시간 지연은
                // 실사용상 무리 없음 - 동기화 완료 이벤트가 없어 무효화 훅을 걸 수 없는 상태라 TTL로 대체.
                .withCacheConfiguration(CARD_FACETS_CACHE, defaultConfig.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration(PRICE_RANKING_CACHE, defaultConfig.entryTtl(Duration.ofHours(26)))
                .build();
    }

    // 목적: Redis 장애/미기동 시에도 /api/cards/facets가 500으로 죽지 않고 DB 조회로 정상 동작하게 하기 위함.
    // 캐시 조회/쓰기 실패 각각 warn 레벨 로그를 남기고 예외는 삼켜서 원본 메서드(@Cacheable 대상)가
    // 정상 실행되게 처리한다. 이 핸들러는 전역 적용이라 나중에 다른 캐시가 추가돼도 같은 정책이 자동 적용된다.
    // 트레이드오프: 캐시가 조용히 실패하면 겉보기엔 정상이지만 매번 DB를 때리고 있을 수 있음 -
    // 아래 warn 로그로 실패율을 확인할 수 있어야 한다.
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 조회 실패, 원본 조회로 폴백합니다: cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("캐시 저장 실패, 캐싱 없이 계속 진행합니다: cache={}, key={}", cache.getName(), key, exception);
            }
        };
    }
}
