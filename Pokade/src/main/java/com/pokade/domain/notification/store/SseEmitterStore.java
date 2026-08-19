package com.pokade.domain.notification.store;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 유저별 SSE Emitter를 인메모리로 관리한다. 유저가 여러 탭/기기에서 동시 구독할 수 있어 유저당 리스트로 보관한다.
// TODO: 서버 인스턴스가 여러 대로 늘어나면 이 저장소는 인스턴스 로컬이라 다른 인스턴스에 연결된 유저에게는
// push할 수 없다. 인스턴스 간 브로드캐스트가 필요해지면 Redis Pub/Sub 등으로 교체해야 한다.
@Slf4j
@Repository
public class SseEmitterStore {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님.
    // required = false: 슬라이스 테스트엔 MeterRegistry 빈이 없어 컨텍스트 로딩이 깨지는 문제(#224 유사)를 막기 위함.
    @Autowired(required = false)
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님.
    // emitters 맵 자체를 상태 객체로 등록해, 스크레이프 시점마다 그 시점의 총 연결 수(유저별 리스트 크기 합)를
    // 즉석에서 계산한다 - 별도 카운터 필드를 직접 증감시키지 않아 save()/remove()의 동시성 로직과 분리된다.
    @PostConstruct
    private void registerActiveConnectionsGauge() {
        meterRegistry.gauge("notification.sse.active.connections", emitters,
                m -> m.values().stream().mapToInt(List::size).sum());
    }

    public void save(Long userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("[SSE] Emitter 등록 userId={}, 현재 연결 수={}", userId, emitters.get(userId).size());
    }

    public void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
        log.info("[SSE] Emitter 제거 userId={}, 남은 연결 수={}", userId, findByUserId(userId).size());
    }

    public List<SseEmitter> findByUserId(Long userId) {
        return emitters.getOrDefault(userId, List.of());
    }

    // 하트비트 등 전체 구독자에게 보낼 때 사용 - 현재 등록된 Emitter들의 스냅샷을 반환한다.
    public List<SseEmitter> findAll() {
        return emitters.values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
