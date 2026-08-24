# 모니터링 가이드 (Actuator / Micrometer / Prometheus / Grafana)

> 이 문서는 원래 개인 설정 파일(`CLAUDE.md`, git 추적 제외)에만 있어서 팀원에게 전달되지 않던
> 내용을 저장소로 옮긴 것이다(#343). 계측을 새로 추가하거나 배포 설정을 만질 때 여기부터 본다.

> **경로 표기**: 파일 경로는 프로젝트 루트인 `Pokade/`(build.gradle.kts가 있는 곳) 기준이다.
> `global/config/MetricsConfig`처럼 확장자 없이 쓴 것은 파일 경로가 아니라 패키지·클래스 약어이며,
> `src/main/java/com/pokade/` 아래에서 찾으면 된다. 이 문서 자체는 저장소 루트 기준
> `Pokade/docs/monitoring.md`이다.

## 한눈에 보기

| 무엇 | 어디 |
| --- | --- |
| `@Timed` 동작(TimedAspect) + SLO 버킷 필터 | `src/main/java/com/pokade/global/config/MetricsConfig.java` |
| 테스트용 MeterRegistry 빈 | `src/test/java/com/pokade/support/TestMetricsConfig.java` |
| SLO 버킷 필터 **로직** 검증 (Spring 없이 필터를 직접 호출) | `src/test/java/com/pokade/global/config/MetricsConfigTest.java` |
| 위 두 빈이 실제로 **등록·적용**되는지 검증 (Spring 컨텍스트) | `src/test/java/com/pokade/global/config/MetricsConfigWiringTest.java` |
| 로컬 관측 스택(Prometheus + Grafana) | `docker-compose.observability.yml`, `observability/` |
| 대시보드 JSON | `observability/grafana/dashboards/*.json` |
| 관리자 지표 API(Prometheus HTTP API 직접 호출) | `src/main/java/com/pokade/domain/admin/metrics/` |

로컬에서 관측 스택 띄우기 (앱은 호스트에서 `./gradlew bootRun`으로 별도 실행):

```bash
# .env에 GRAFANA_ADMIN_PASSWORD를 먼저 채워야 한다(비어 있으면 즉시 실패)
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
# Prometheus http://127.0.0.1:9090 / Grafana http://127.0.0.1:3001
```

## 현재 지표 인벤토리

SLO 버킷이 붙는 타이머는 `MetricsConfig`가 이름으로 지정한 6개뿐이다. 나머지는 Counter/Gauge이거나
버킷 대상이 아니다.

| 지표 | 종류 | SLO 버킷 | 소비처 |
| --- | --- | --- | --- |
| `card.search.duration` | Timer | 조회성 | card 대시보드 (평균 / P95 / 200ms 달성률) |
| `card.search.keyword.duration` | Timer | 조회성 | card 대시보드 |
| `watchlist.add.duration` | Timer | 조회성 | watchlist 대시보드 |
| `watchlist.update.duration` | Timer | 조회성 | watchlist 대시보드 |
| `price.ranking.duration` | Timer | 조회성 | 없음 |
| `ai.grade.duration` | Timer | AI 진단 | ai 대시보드 |
| `ai.grade.vision.duration` | Timer | **없음** | ai 대시보드(평균) |
| `chat.llm.duration` | Timer | AI 진단(LLM 호출 특성 공유) | 없음 |
| `card.ratelimit.allowed` / `.rejected` | Counter | - | card 대시보드 |
| `card.view.increment.calls` / `card.grade.batch.calls` | Counter | - | card 대시보드 |
| `watchlist.notify.immediate.calls` / `.already_claimed.calls` | Counter | - | watchlist 대시보드 |
| `notification.sse.active.connections` | Gauge | - | watchlist 대시보드 |
| `notification.sse.heartbeat.failure.calls` / `.push.failure.calls` | Counter | - | watchlist 대시보드 |
| `site.visits` | Counter | - | **관리자 대시보드(제품 기능)** |
| `price.chart.requests` / `price.ranking.requests` | Counter | - | 없음 |
| `ai.grade.result` / `.local_fail` / `.vision.retries` | Counter | - | ai 대시보드 |
| `ai.grade.cache.hits` | Counter | - | ai 대시보드 |
| `chat.llm.calls`(status=success/error) | Counter | - | 없음 |
| `chat.llm.grounding_fail` | Counter | - | 없음 |

`site.visits`는 `domain/admin/metrics`가 `site_visits_total`로 조회하는 **실제 제품 기능**이므로
지우면 관리자 대시보드가 깨진다. 나머지 계측은 Grafana 패널만 의존한다.

## 모니터링 확장 가이드 — 새 도메인에 @Timed 붙이는 법

계측을 새로 추가할 때 **두 단계 다** 해야 한다. 1단계만 하면 지표는
쌓이는데 p95/SLO 달성률을 못 뽑고, 그 사실이 아무 데도 안 드러난다.

**1단계 — 서비스 메서드에 @Timed 선언**

```java
@Timed(value = "listing.search.duration")
public List<ListingResponse> search(...) { ... }
```

TimedAspect는 `global/config/MetricsConfig`에 이미 등록돼 있어 추가 설정은
필요 없다. 단 **self-invocation(같은 빈 내부에서의 호출)은 프록시를 안 거쳐
계측되지 않는다** — CardQueryService.searchByKeyword()가 이 이유로 별도
메서드에 애노테이션을 유지하고 있으니 참고할 것.

**2단계 — MetricsConfig의 SLO 대상 목록에 이름 추가**

`MetricsConfig.QUERY_API_TIMERS`(조회성) 또는 `AI_GRADE_TIMER`(초 단위 작업)에
지표명을 추가한다. 이걸 빠뜨리면 count/sum/max만 노출돼서
`histogram_quantile`도, "200ms 이내 비율"도 계산할 수 없다.

**이름 문자열 매칭이라 빠뜨려도 컴파일 에러가 안 난다.** 그래서
`MetricsConfigTest`가 card/watchlist에 대해서는 애노테이션 값을 리플렉션으로
읽어와 버킷이 실제로 붙는지 검증한다 — 새 도메인도 거기에 추가해두면
나중에 이름을 바꿨을 때 대시보드가 조용히 비는 대신 테스트가 먼저 깨진다.

**테스트에서 MeterRegistry 채우기** (#343에서 필드 주입 → 생성자 주입으로 통일)

| 테스트 형태 | 방법 |
| --- | --- |
| 슬라이스(@WebMvcTest/@DataJpaTest) | `support/TestMetricsConfig.class`를 함께 @Import |
| new로 만드는 단위 테스트 | 생성자에 `new SimpleMeterRegistry()` 전달 |
| @InjectMocks | `@Spy MeterRegistry meterRegistry = new SimpleMeterRegistry()` |

마지막 줄이 중요하다. `@Mock MeterRegistry`로 두면 `counter()`가 null을
돌려줘서 생성자 안에서 NPE가 나고, Mockito가 `InjectMocksException`으로
감싸버려 원인이 안 보인다(`AiGradeServiceTest`가 정확히 이 이유로 실패했다가
`@Spy MeterRegistry meterRegistry = new SimpleMeterRegistry()`로 고쳐진
사례다 — 새 테스트를 추가할 때 그대로 참고할 것).

Counter/Gauge를 직접 쓸 때도 MeterRegistry는 생성자로 받는다.

**지표명 → Prometheus 이름 변환** (대시보드 PromQL 작성 시)

| 종류 | 예시 |
| --- | --- |
| Timer | `card.search.duration` → `card_search_duration_seconds_{count,sum,bucket}` |
| Counter | `site.visits` → `site_visits_total` |
| Gauge | `notification.sse.active.connections` → `notification_sse_active_connections` |

SLO 버킷의 `le` 라벨은 **초 단위**다(200ms → `le="0.2"`, 1s → `le="1.0"`).
MetricsConfig는 나노초로 선언하지만 노출은 초로 변환된다 — 헷갈리면
PrometheusMeterRegistry로 `scrape()`를 한 번 찍어보면 바로 확인된다.

대시보드는 `observability/grafana/dashboards/*.json`에 추가한다. provisioning
provider가 `updateIntervalSeconds: 30`으로 폴링하므로 파일만 고치면 30초 안에
반영된다(`version` 필드를 올리는 건 변경 추적용 관례일 뿐 갱신 조건이 아니다).
`allowUiUpdates: false`라서 **Grafana UI에서 고친 건 다음 폴링 때 덮어써진다**
— 반드시 JSON 파일 쪽을 고칠 것.

## prod의 Actuator 포트 분리 (8081 / 127.0.0.1)

application-prod.yaml에만 아래 설정이 있다(dev는 본 포트 8080을 그대로 씀):

```yaml
management:
  server:
    port: ${MANAGEMENT_PORT:8081}
    address: ${MANAGEMENT_ADDRESS:127.0.0.1}
```

이 때문에 dev에서 되던 게 prod에서 그대로 되지 않는다:

- **현재 관측 스택은 로컬 전용이다.** `observability/prometheus.yml`이
  `host.docker.internal:8080`을 스크랩하는데, prod에선 actuator가 그 포트에
  없다. prod에 붙이려면 스크랩 타깃을 8081로 바꿔야 한다.
- **SecurityConfig의 `/actuator/health`·`/actuator/prometheus` 화이트리스트는
  prod 본 포트에선 무의미하다.** 해당 경로가 본 포트에 존재하지 않는다.
- **127.0.0.1 바인딩이라 원격/다른 컨테이너에서 스크랩할 수 없다.**
  Prometheus를 같은 호스트에 띄우거나 `MANAGEMENT_ADDRESS`를 열어야 하는데,
  열면 인증 없이 지표가 노출되므로 방화벽/보안그룹으로 막아야 한다.
  → 배포 시 팀 확인 필요한 항목.

노출 범위(`health,prometheus,metrics`)는 dev/prod가 같다. 다만
`/actuator/metrics`는 AUTH_WHITELIST에 없어 JWT가 필요하다(사실상 미사용).

관리자 대시보드(`domain/admin/metrics`)는 actuator가 아니라 `PROMETHEUS_BASE_URL`로
Prometheus HTTP API를 직접 호출하므로 이 포트 분리와 무관하다.

## 알려진 미해결 제약

- `/actuator/health`가 DOWN으로 확인된 적이 있다(원인 미조사, mail/redis 인디케이터 추정).
  팀 전체 이슈일 수 있어 재확인 필요.
- `SecurityConfig`의 `AUTH_WHITELIST`에 `/actuator/health`가 **정확히 그 경로만** 등록돼 있어
  `/actuator/health/liveness` 같은 하위 경로는 인증이 걸린다.
- `management.endpoints.web.exposure.include`에 `metrics`가 있지만 `/actuator/metrics`는
  화이트리스트에 없어 JWT가 필요하다(사실상 미사용).
- SLO 대상 목록이 `global/config`의 공유 파일 한 곳에 모여 있어, 도메인이 늘수록 모든 담당자가
  같은 파일을 편집하게 된다. `@Timed(serviceLevelObjectives = ...)`로 각 도메인이 자기 SLO를
  선언하는 구조로 옮기는 방안은 **별도 이슈로 논의 필요**(이번 #343 범위 밖).


---

# 부록: 스키마 마이그레이션(Flyway)

모니터링 자체와는 별개지만, 아래 "@DataJpaTest 슬라이스는 Flyway를 안 돌린다" 함정이 계측 테스트
구성(`TestMetricsConfig` / `AbstractIntegrationTest` 병용)과 직접 얽혀 있어 같이 둔다.

**스키마를 바꿀 땐 `src/main/resources/db/migration/V{n}__설명.sql`을 추가한다.**
`schema.sql`은 더 이상 존재하지 않는다 — V1__baseline_schema.sql이 그 자리를
대신하고, 남아있는 `data.sql`은 시드 데이터 전용이다.
(`sql.init.mode: always`가 아직 dev에 있는 건 그 data.sql 때문이다.)

**`spring.flyway.enabled: false`를 보고 "Flyway 안 쓰는구나"로 오해하지 말 것.**
이건 Flyway를 끈 게 아니라 **자동설정만** 끈 것이다. Boot 4.0.7에서
FlywayAutoConfiguration + HibernateJpaConfiguration 조합이
"Circular depends-on relationship between 'flyway' and 'entityManagerFactory'"로
부팅 자체를 실패시켜서, 자동설정을 끄고 `global/config/FlywayMigrationListener`가
ApplicationEnvironmentPreparedEvent 시점에 Flyway를 직접 실행한다
(entityManagerFactory가 만들어지기 전에 끝나도록).

→ **결론: dev에서 앱만 다시 띄우면 새 마이그레이션이 자동 적용된다.**
   PR을 pull 받은 뒤 수동 ALTER TABLE을 칠 필요 없다.
   적용 여부는 `flyway_schema_history` 테이블로 확인한다:

```bash
docker exec pokade-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"'
```

**함정 1 — 리스너는 main()에서만 등록된다.**
`PokadeApplication.main()`이 `app.addListeners(new FlywayMigrationListener())`로
직접 붙이는 방식이라(spring.factories 등록이 아님), **테스트는 이 리스너를 타지
않는다.** 그래서 통합 테스트는 `support/AbstractIntegrationTest`가 static 블록에서
Flyway를 따로 돌린다.

**함정 2 — @DataJpaTest 슬라이스는 Flyway를 안 돌린다.**
새 `V{n}`을 추가하면 그 컬럼을 쓰는 `@DataJpaTest`가 깨진다(테이블은 있는데
컬럼이 없는 상태). 해결은 그 테스트를 `AbstractIntegrationTest` 상속으로
전환하는 것이다 — testcontainers로 매 클래스마다 새 Postgres를 띄우고 V1부터
전부 적용한다. develop이 #338에서 WatchlistTargetPriceNotice*Test 2개를
정확히 이 이유로 전환했다(V12 추가 때문).

**주의: 완전 초기화(`docker compose down -v`)는 목데이터가 날아간다.**
Flyway가 알아서 반영하므로 초기화할 이유는 거의 없다.
