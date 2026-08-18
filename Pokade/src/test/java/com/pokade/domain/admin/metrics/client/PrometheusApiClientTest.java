package com.pokade.domain.admin.metrics.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrometheusApiClientTest {

    private static final String BASE_URL = "http://localhost:9090";

    // 2026-08-18에 실제로 겪은 회귀: RestClient의 uriBuilder(Function<UriBuilder,URI>) 경로가 PromQL
    // 라벨 매처의 {}를 URI 템플릿 변수로 오인해 라벨 필터 쿼리를 전부 빈 값으로 만들었다. 이 테스트는
    // HTTP 호출 없이 URI 인코딩만 검증해서, 같은 종류의 인코딩 회귀를 값싸게 잡는다.
    @Test
    @DisplayName("PromQL의 라벨 매처 { }가 인코딩됐다가 원본 그대로 디코딩된다")
    void buildQueryUri_roundTripsLabelMatcherBraces() {
        String promql = "sum(http_server_requests_seconds_count{status=~\"5..\"})";

        URI uri = PrometheusApiClient.buildQueryUri(BASE_URL, promql);

        assertThat(uri.getRawQuery()).doesNotContain("{").doesNotContain("}");
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                .isEqualTo("query=" + promql);
    }

    @Test
    @DisplayName("query_range URI도 라벨 매처를 안전하게 인코딩하고 start/end/step을 포함한다")
    void buildQueryRangeUri_roundTripsAndIncludesRangeParams() {
        String promql = "sum(increase(http_server_requests_seconds_count{uri=\"/api/ai/grade\"}[1h]))";

        URI uri = PrometheusApiClient.buildQueryRangeUri(BASE_URL, promql, 1000L, 2000L, "1h");

        String decoded = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("query=" + promql + "&start=1000&end=2000&step=1h");
    }

    @Test
    @DisplayName("queryScalar가 실제 요청을 보내고(라벨 매처 포함) 정상 응답을 파싱한다")
    void queryScalar_sendsCorrectlyEncodedRequestAndParsesResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        PrometheusApiClient client = new PrometheusApiClient(builder, new PrometheusProperties(BASE_URL));
        String promql = "sum(http_server_requests_seconds_count{status=~\"5..\"})";

        mockServer.expect(req -> {
                    String decoded = URLDecoder.decode(req.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertThat(decoded).isEqualTo("query=" + promql);
                })
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"status":"success","data":{"resultType":"vector",
                        "result":[{"metric":{},"value":[1700000000,"0.85"]}]}}
                        """,
                        MediaType.APPLICATION_JSON));

        Optional<Double> value = client.queryScalar(promql);

        assertThat(value).contains(0.85);
        mockServer.verify();
    }

    @Test
    @DisplayName("queryRange가 여러 포인트를 시간순으로 파싱하고 NaN 포인트는 건너뛴다")
    void queryRange_parsesPointsAndSkipsNaN() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        PrometheusApiClient client = new PrometheusApiClient(builder, new PrometheusProperties(BASE_URL));

        mockServer.expect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"status":"success","data":{"resultType":"matrix",
                        "result":[{"metric":{},"values":[[1000,"3.0"],[2000,"NaN"],[3000,"5.0"]]}]}}
                        """,
                        MediaType.APPLICATION_JSON));

        List<PrometheusClient.PrometheusPoint> points = client.queryRange("up", 0L, 4000L, "1h");

        assertThat(points).containsExactly(
                new PrometheusClient.PrometheusPoint(1000L, 3.0),
                new PrometheusClient.PrometheusPoint(3000L, 5.0));
    }
}
