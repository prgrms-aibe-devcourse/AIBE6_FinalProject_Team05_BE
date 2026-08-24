package com.pokade.domain.notification.store;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class SseEmitterStore {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // 계측 주입 규칙은 support/TestMetricsConfig javadoc 참조(#343).
    // 위 emitters는 final이지만 선언과 동시에 초기화돼 @RequiredArgsConstructor 대상에서 빠지므로,
    // 생성자 파라미터는 이 필드 하나뿐이다.
    private final MeterRegistry meterRegistry;

    // 운영 계측 - #258 도입, 워치리스트/알림 대시보드가 사용 중.
    // emitters 맵 자체를 상태 객체로 등록해, 스크레이프 시점마다 그 시점의 총 연결 수(유저별 리스트 크기 합)를
    // 즉석에서 계산한다 - 별도 카운터 필드를 직접 증감시키지 않아 save()/remove()의 동시성 로직과 분리된다.
    @PostConstruct
    private void registerActiveConnectionsGauge() {
        meterRegistry.gauge("notification.sse.active.connections", emitters,
                m -> m.values().stream().mapToInt(List::size).sum());
    }

    // #386: computeIfAbsent(...).add(emitter)가 아니라 compute()를 쓰는 이유 - 전자는 "리스트 획득"만
    // 원자적이고 실제 add는 맵 잠금 밖에서 일어난다. 그 사이 remove()가 마지막 Emitter를 지워 맵 엔트리를
    // 삭제하면(아래 참고) 새 Emitter는 맵에서 떨어진 리스트에 담겨 findByUserId/findAll에 영원히 안 잡힌다 -
    // 알림도 하트비트도 못 받는 유령 연결이 된다(새로고침으로 기존 연결이 끊기며 새 연결이 열릴 때 발생).
    // compute()의 remapping 함수는 해당 bin 잠금 안에서 실행되므로 "리스트 생성 → 추가 → 맵 반영"이 한
    // 덩어리가 되고, remove()와 어느 순서로 겹쳐도 결과가 안전하다:
    //   remove가 먼저 → 엔트리 삭제 → 여기서 list==null을 보고 새 리스트를 만들어 넣는다
    //   save가 먼저   → 리스트 크기 1 → remove가 자기 것만 빼도 비지 않아 엔트리가 유지된다
    // 반환값(항상 non-null)을 그대로 로깅에 쓴다 - 예전의 emitters.get(userId).size()는 같은 경합에서
    // null.size() NPE로 subscribe 자체를 500으로 만들었다.
    public void save(Long userId, SseEmitter emitter) {
        List<SseEmitter> current = emitters.compute(userId, (key, list) -> {
            List<SseEmitter> target = list != null ? list : new CopyOnWriteArrayList<>();
            target.add(emitter);
            return target;
        });
        log.info("[SSE] Emitter 등록 userId={}, 현재 연결 수={}", userId, current.size());
    }

    // 리스트가 비면 null을 반환해 맵 엔트리까지 지운다 - 유저가 전부 접속을 끊었는데 빈 리스트만 남아
    // 맵이 무한히 커지는 것을 막는다.
    // computeIfPresent의 반환값을 그대로 쓴다(엔트리가 지워졌으면 null) - 예전엔 findByUserId()로 다시
    // 조회해 크기를 찍었는데, 그 재조회 자체가 두 번째 경합 창이었다(그 사이 다른 탭이 등록하면 로그가
    // 방금 제거한 결과와 다른 수를 찍는다).
    public void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> remaining = emitters.computeIfPresent(userId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
        log.info("[SSE] Emitter 제거 userId={}, 남은 연결 수={}", userId, remaining == null ? 0 : remaining.size());
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
