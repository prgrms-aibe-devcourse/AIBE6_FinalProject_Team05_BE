package com.pokade.domain.admin.metrics.service;

import com.pokade.domain.admin.metrics.client.PrometheusClient;
import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import com.pokade.domain.admin.metrics.dto.AdminMetricCardResponse;
import com.pokade.domain.admin.metrics.dto.AdminMetricSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private static final long SERIES_LOOKBACK_HOURS = 24;
    private static final String SERIES_STEP = "5m";

    // 새 지표를 추가하려면: 서비스 코드에 Micrometer로 계측 후, 여기에 정의 한 줄만 추가하면 된다.
    private static final List<CardDefinition> CARDS = List.of(
            new CardDefinition("priceChartRequests", "시세 차트 조회 수", "sum(price_chart_requests_total)", "회"),
            new CardDefinition("priceRankingRequests", "랭킹 조회 수", "sum(price_ranking_requests_total)", "회"),
            new CardDefinition("priceRankingAvgDuration", "랭킹 평균 응답시간",
                    "sum(price_ranking_duration_seconds_sum) / sum(price_ranking_duration_seconds_count) * 1000", "ms"),
            new CardDefinition("priceSummariesAvgBatchSize", "시세 배치조회 평균 카드 수",
                    "sum(price_summaries_batch_size_sum) / sum(price_summaries_batch_size_count)", "개"),
            new CardDefinition("httpTotalRequests", "전체 HTTP 요청 수", "sum(http_server_requests_seconds_count)", "회"),
            new CardDefinition("httpErrorRate", "HTTP 5xx 에러율",
                    "(sum(http_server_requests_seconds_count{status=~\"5..\"}) or vector(0)) "
                            + "/ sum(http_server_requests_seconds_count) * 100", "%")
    );

    private static final List<SeriesDefinition> SERIES = List.of(
            new SeriesDefinition("httpRequestRate", "시간대별 HTTP 요청량",
                    "sum(increase(http_server_requests_seconds_count[5m]))", "회/5분")
    );

    private final PrometheusClient prometheusClient;

    public AdminDashboardResponse getDashboard() {
        List<AdminMetricCardResponse> cards = CARDS.stream().map(this::toCard).toList();
        List<AdminMetricSeriesResponse> series = SERIES.stream().map(this::toSeries).toList();
        return new AdminDashboardResponse(cards, series);
    }

    // Prometheus 조회 실패(연결 불가, 지표 없음 등)가 카드 하나 때문에 대시보드 전체를 에러로 만들면 안 된다.
    private AdminMetricCardResponse toCard(CardDefinition def) {
        Double value = safeQueryScalar(def.promql());
        return new AdminMetricCardResponse(def.key(), def.label(), value, def.unit());
    }

    private AdminMetricSeriesResponse toSeries(SeriesDefinition def) {
        Instant end = Instant.now();
        Instant start = end.minus(SERIES_LOOKBACK_HOURS, ChronoUnit.HOURS);
        List<AdminMetricSeriesResponse.Point> points;
        try {
            points = prometheusClient.queryRange(def.promql(), start.getEpochSecond(), end.getEpochSecond(), SERIES_STEP)
                    .stream()
                    .map(p -> new AdminMetricSeriesResponse.Point(p.epochSeconds(), p.value()))
                    .toList();
        } catch (Exception e) {
            points = List.of();
        }
        return new AdminMetricSeriesResponse(def.key(), def.label(), def.unit(), points);
    }

    private Double safeQueryScalar(String promql) {
        try {
            return prometheusClient.queryScalar(promql).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private record CardDefinition(String key, String label, String promql, String unit) {
    }

    private record SeriesDefinition(String key, String label, String promql, String unit) {
    }
}
