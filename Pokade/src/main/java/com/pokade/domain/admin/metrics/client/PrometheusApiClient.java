package com.pokade.domain.admin.metrics.client;

import com.pokade.domain.admin.metrics.client.dto.PrometheusQueryResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class PrometheusApiClient implements PrometheusClient {

    private final RestClient restClient;
    private final String baseUrl;

    public PrometheusApiClient(PrometheusProperties properties) {
        this.baseUrl = properties.baseUrl();
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Optional<Double> queryScalar(String promql) {
        // PromQL은 라벨 매처에 { }를 쓰는데, RestClient의 uriBuilder(Function<UriBuilder,URI>) 경로는
        // 쿼리 파라미터 값 안의 {}도 URI 템플릿 변수로 취급해 깨진다 - URLEncoder로 직접 인코딩한
        // 완성된 URI를 넘겨 템플릿 해석 자체를 우회한다.
        URI uri = URI.create(baseUrl + "/api/v1/query?query=" + encode(promql));
        PrometheusQueryResponse response = restClient.get().uri(uri).retrieve().body(PrometheusQueryResponse.class);

        return firstResult(response)
                .map(PrometheusQueryResponse.Result::value)
                .flatMap(PrometheusApiClient::parseValueAt1);
    }

    @Override
    public List<PrometheusPoint> queryRange(String promql, long startEpochSeconds, long endEpochSeconds, String step) {
        URI uri = URI.create(baseUrl + "/api/v1/query_range?query=" + encode(promql)
                + "&start=" + startEpochSeconds + "&end=" + endEpochSeconds + "&step=" + encode(step));
        PrometheusQueryResponse response = restClient.get().uri(uri).retrieve().body(PrometheusQueryResponse.class);

        return firstResult(response)
                .map(PrometheusQueryResponse.Result::values)
                .map(values -> values.stream()
                        .map(PrometheusApiClient::parsePoint)
                        .flatMap(Optional::stream)
                        .toList())
                .orElseGet(List::of);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // 여러 시리즈(라벨 조합)가 나올 수 있지만, 우리는 항상 sum(...) 등으로 하나로 합쳐서 쿼리하므로 첫 번째만 쓴다.
    private static Optional<PrometheusQueryResponse.Result> firstResult(PrometheusQueryResponse response) {
        if (response == null || response.data() == null || response.data().result() == null
                || response.data().result().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.data().result().get(0));
    }

    // Prometheus의 [timestamp, "value"] 쌍에서 인덱스 1(값)만 파싱한다. instant/range 공통 포맷.
    private static Optional<Double> parseValueAt1(List<Object> pair) {
        if (pair == null || pair.size() < 2) {
            return Optional.empty();
        }
        return parseNumber(String.valueOf(pair.get(1)));
    }

    private static Optional<PrometheusPoint> parsePoint(List<Object> pair) {
        if (pair == null || pair.size() < 2) {
            return Optional.empty();
        }
        long epochSeconds = (long) Double.parseDouble(String.valueOf(pair.get(0)));
        return parseNumber(String.valueOf(pair.get(1))).map(v -> new PrometheusPoint(epochSeconds, v));
    }

    // 데이터가 없는 구간은 값이 "NaN"으로 내려온다 - 0이 아니라 결측치이므로 스킵한다.
    private static Optional<Double> parseNumber(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isNaN(value) ? Optional.empty() : Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
