package com.pokade.domain.notification.store;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 유저별 SSE Emitter를 인메모리로 관리한다. 유저가 여러 탭/기기에서 동시 구독할 수 있어 유저당 리스트로 보관한다.
// TODO: 서버 인스턴스가 여러 대로 늘어나면 이 저장소는 인스턴스 로컬이라 다른 인스턴스에 연결된 유저에게는
// push할 수 없다. 인스턴스 간 브로드캐스트가 필요해지면 Redis Pub/Sub 등으로 교체해야 한다.
@Repository
public class SseEmitterStore {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void save(Long userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
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
