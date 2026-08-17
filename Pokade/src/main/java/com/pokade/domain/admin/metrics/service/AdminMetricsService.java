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

    // Spring Boot가 자동 계측하는 http_server_requests_seconds_*의 uri 태그는 실제 경로가 아니라
    // 매핑 패턴 그대로("{id}" 포함) 찍힌다 - AiGradeController/TradeController의 @*Mapping 값과 동일해야 매칭된다.
    private static final String AI_GRADE_URI = "/api/ai/grade";
    private static final String TRADE_CONFIRM_URI = "/api/trades/{id}/confirm";

    // 새 지표를 추가하려면: (필요 시) 서비스 코드에 Micrometer로 계측 후, 여기에 정의 한 줄만 추가하면 된다.
    // AI 진단/거래 확정처럼 이미 자동 계측되는 http_server_requests_seconds_count를 uri로 필터링해 쓰면
    // 별도 계측 없이 바로 지표를 늘릴 수 있다.
    private static final List<CardDefinition> CARDS = List.of(
            // 카운터 원값 자체가 "누적" - 마지막 BE 재시작 이후로 쌓인 합계다(재시작 시 0으로 리셋됨).
            new CardDefinition("totalVisits", "총 방문자 수", "site_visits_total", "명",
                    "오늘 증가", "increase(site_visits_total[24h])"),
            new CardDefinition("aiGradeToday", "오늘 AI 등급진단 사용 수",
                    "sum(increase(http_server_requests_seconds_count{uri=\"" + AI_GRADE_URI
                            + "\",method=\"POST\",status=\"200\"}[24h]))", "회"),
            new CardDefinition("tradesConfirmedToday", "오늘 거래 확정 수",
                    "sum(increase(http_server_requests_seconds_count{uri=\"" + TRADE_CONFIRM_URI
                            + "\",method=\"PATCH\",status=\"200\"}[24h]))", "건"),
            new CardDefinition("httpErrorRate24h", "HTTP 5xx 에러율(24h)",
                    "(sum(increase(http_server_requests_seconds_count{status=~\"5..\"}[24h])) or vector(0)) "
                            + "/ sum(increase(http_server_requests_seconds_count[24h])) * 100", "%"),
            new CardDefinition("avgLatency24h", "평균 응답 지연(24h)",
                    "sum(increase(http_server_requests_seconds_sum[24h])) "
                            + "/ sum(increase(http_server_requests_seconds_count[24h])) * 1000", "ms")
    );

    // group: 같은 group끼리는 한 차트에 겹쳐 그릴 수 있다는 뜻(FE가 이 값으로 묶는다) - 단위/스케일이
    // 다른 지표(%, ms)를 개수(회/건)와 한 차트에 억지로 합치면 서로 안 보이게 되므로 그룹을 분리한다.
    // promqlTemplate의 %1$s는 AdminMetricsPeriod.step으로 채워진다 - 조회 단위(10분/1시간/1일)와
    // increase()의 구간 크기가 어긋나면(예: 10분 단위인데 [1h]로 누적) 값이 서로 겹쳐서 왜곡된다.
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
