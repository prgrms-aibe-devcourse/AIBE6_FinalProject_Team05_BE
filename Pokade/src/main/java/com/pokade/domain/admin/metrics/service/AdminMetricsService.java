package com.pokade.domain.admin.metrics.service;

import com.pokade.domain.admin.metrics.AdminMetricsPeriod;
import com.pokade.domain.admin.metrics.client.PrometheusClient;
import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import com.pokade.domain.admin.metrics.dto.AdminMetricCardResponse;
import com.pokade.domain.admin.metrics.dto.AdminMetricSeriesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    // uri 태그는 컨트롤러 매핑 패턴 그대로("{id}" 포함) 찍히므로 실제 매핑과 정확히 같아야 하며, 어긋나면
    // ControllerMappingAssertionTest가 잡아준다(그래서 package-private).
    static final String AI_GRADE_URI = "/api/ai/grade";
    static final String TRADE_CONFIRM_URI = "/api/trades/{id}/confirm";

    // 블루-그린 배포 때마다(=BE 프로세스 재시작 때마다) 카운터 원값이 0으로 리셋되는 문제(사용자 리포트) -
    // Micrometer 카운터 자체는 JVM 인메모리라 재시작을 못 버티지만, Prometheus가 스크랩해 쌓아둔 시계열은
    // 재시작과 무관하게 남아있다. increase(...)는 구간 안의 카운터 리셋을 자동으로 감지해 보정하므로,
    // 원시값을 그대로 읽는 대신 TOTAL_WINDOW 구간의 increase 합을 "누적 총합"으로 취급하면 재시작에도
    // 끊기지 않는다. 또한 sum()으로 묶어야 blue/green 두 슬롯(app-a/app-b)이 서로 다른 instance 라벨의
    // 별도 시계열로 잡히는 것도 하나로 합쳐진다(PrometheusApiClient.firstResult()의 "항상 sum(...)으로
    // 하나로 합쳐서 쿼리한다"는 전제와도 일치 - 이 세 카드만 그 전제를 어기고 원시값을 쓰고 있었다).
    // TOTAL_WINDOW는 Prometheus 보존 기간(docker-compose.observability*.yml의
    // --storage.tsdb.retention.time)과 맞춰뒀다 - 그보다 길게 잡아도 의미가 없다(그 이전 데이터가 이미
    // 삭제됨). 보존 기간을 늘리면 이 상수도 함께 늘려야 한다.
    private static final String TOTAL_WINDOW = "90d";

    // 새 지표는 (필요 시 계측 후) 이 리스트에 한 줄만 추가하면 되고, 이미 자동 계측되는 지표는 uri 필터링만으로 추가 가능하다.
    private static final List<CardDefinition> CARDS = List.of(
            new CardDefinition("totalVisits", "총 방문자 수",
                    "sum(increase(site_visits_total[" + TOTAL_WINDOW + "]))", "명",
                    "오늘 증가", "increase(site_visits_total[24h])"),
            new CardDefinition("aiGradeTotal", "AI 등급진단 총 사용 수",
                    "sum(increase(http_server_requests_seconds_count{uri=\"" + AI_GRADE_URI
                            + "\",method=\"POST\",status=\"200\"}[" + TOTAL_WINDOW + "]))", "회",
                    "오늘 증가", "sum(increase(http_server_requests_seconds_count{uri=\"" + AI_GRADE_URI
                            + "\",method=\"POST\",status=\"200\"}[24h]))"),
            new CardDefinition("tradesConfirmedTotal", "거래 확정 총 건수",
                    "sum(increase(http_server_requests_seconds_count{uri=\"" + TRADE_CONFIRM_URI
                            + "\",method=\"PATCH\",status=\"200\"}[" + TOTAL_WINDOW + "]))", "건",
                    "오늘 증가", "sum(increase(http_server_requests_seconds_count{uri=\"" + TRADE_CONFIRM_URI
                            + "\",method=\"PATCH\",status=\"200\"}[24h]))"),
            new CardDefinition("httpErrorRate24h", "HTTP 5xx 에러율(24h)",
                    "(sum(increase(http_server_requests_seconds_count{status=~\"5..\"}[24h])) or vector(0)) "
                            + "/ sum(increase(http_server_requests_seconds_count[24h])) * 100", "%"),
            new CardDefinition("avgLatency24h", "평균 응답 지연(24h)",
                    "sum(increase(http_server_requests_seconds_sum[24h])) "
                            + "/ sum(increase(http_server_requests_seconds_count[24h])) * 1000", "ms")
    );

    // group이 같으면 스케일이 맞아 한 차트에 겹쳐 그릴 수 있다(FE가 구분); %1$s는 period.step으로 채워져 increase() 구간을 조회 단위에 맞춘다.
    private static final List<SeriesDefinition> SERIES = List.of(
            new SeriesDefinition("visits", "방문 수", "increase(site_visits_total[%1$s])", "회", "activity"),
            new SeriesDefinition("aiGrade", "AI 진단 사용 수",
                    "sum(increase(http_server_requests_seconds_count{uri=\"" + AI_GRADE_URI
                            + "\",method=\"POST\",status=\"200\"}[%1$s]))", "회", "activity"),
            new SeriesDefinition("tradesConfirmed", "거래 확정 수",
                    "sum(increase(http_server_requests_seconds_count{uri=\"" + TRADE_CONFIRM_URI
                            + "\",method=\"PATCH\",status=\"200\"}[%1$s]))", "건", "activity"),
            new SeriesDefinition("httpErrorRate", "HTTP 5xx 에러율",
                    "(sum(increase(http_server_requests_seconds_count{status=~\"5..\"}[%1$s])) or vector(0)) "
                            + "/ sum(increase(http_server_requests_seconds_count[%1$s])) * 100", "%", "errorRate"),
            new SeriesDefinition("avgLatency", "평균 응답 지연",
                    "sum(increase(http_server_requests_seconds_sum[%1$s])) "
                            + "/ sum(increase(http_server_requests_seconds_count[%1$s])) * 1000", "ms", "latency")
    );

    private final PrometheusClient prometheusClient;

    public AdminDashboardResponse getDashboard(String periodCode) {
        AdminMetricsPeriod period = AdminMetricsPeriod.from(periodCode);
        List<AdminMetricCardResponse> cards = CARDS.stream().map(this::toCard).toList();
        List<AdminMetricSeriesResponse> series = SERIES.stream().map(def -> toSeries(def, period)).toList();
        return new AdminDashboardResponse(cards, series);
    }

    // Prometheus 조회 실패(연결 불가, 지표 없음 등)가 카드 하나 때문에 대시보드 전체를 에러로 만들면 안 된다.
    private AdminMetricCardResponse toCard(CardDefinition def) {
        Double value = safeQueryScalar(def.promql());
        Double subValue = def.subPromql() != null ? safeQueryScalar(def.subPromql()) : null;
        return new AdminMetricCardResponse(def.key(), def.label(), value, def.unit(), def.subLabel(), subValue);
    }

    private AdminMetricSeriesResponse toSeries(SeriesDefinition def, AdminMetricsPeriod period) {
        Instant end = Instant.now();
        Instant start = end.minus(period.getLookbackHours(), ChronoUnit.HOURS);
        String promql = def.promqlTemplate().formatted(period.getStep());
        List<AdminMetricSeriesResponse.Point> points;
        try {
            points = prometheusClient.queryRange(promql, start.getEpochSecond(), end.getEpochSecond(), period.getStep())
                    .stream()
                    .map(p -> new AdminMetricSeriesResponse.Point(p.epochSeconds(), p.value()))
                    .toList();
        } catch (Exception e) {
            log.warn("Prometheus 시계열 조회 실패: key={}, promql={}", def.key(), promql, e);
            points = List.of();
        }
        return new AdminMetricSeriesResponse(def.key(), def.label(), def.unit(), def.group(), points);
    }

    private Double safeQueryScalar(String promql) {
        try {
            return prometheusClient.queryScalar(promql).orElse(null);
        } catch (Exception e) {
            log.warn("Prometheus 카드 조회 실패: promql={}", promql, e);
            return null;
        }
    }

    private record CardDefinition(String key, String label, String promql, String unit,
                                   String subLabel, String subPromql) {
        CardDefinition(String key, String label, String promql, String unit) {
            this(key, label, promql, unit, null, null);
        }
    }

    private record SeriesDefinition(String key, String label, String promqlTemplate, String unit, String group) {
    }
}
