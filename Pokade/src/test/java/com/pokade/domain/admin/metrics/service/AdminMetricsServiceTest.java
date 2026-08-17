package com.pokade.domain.admin.metrics.service;

import com.pokade.domain.admin.metrics.client.PrometheusClient;
import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceTest {

    @Mock
    private PrometheusClient prometheusClient;

    @InjectMocks
    private AdminMetricsService adminMetricsService;

    @Test
    @DisplayName("Prometheus가 값을 정상 반환하면 카드/시리즈에 그대로 채워진다")
    void getDashboard_success() {
        given(prometheusClient.queryScalar(anyString())).willReturn(Optional.of(42.0));
        given(prometheusClient.queryRange(anyString(), any(Long.class), any(Long.class), anyString()))
                .willReturn(List.of(new PrometheusClient.PrometheusPoint(1000L, 3.0)));

        AdminDashboardResponse response = adminMetricsService.getDashboard();

        assertThat(response.cards()).isNotEmpty();
        assertThat(response.cards()).allSatisfy(card -> assertThat(card.value()).isEqualTo(42.0));
        assertThat(response.cards())
                .filteredOn(card -> card.key().equals("totalVisits"))
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.subLabel()).isEqualTo("오늘 증가");
                    assertThat(card.subValue()).isEqualTo(42.0);
                });
        assertThat(response.series()).isNotEmpty();
        assertThat(response.series().get(0).points()).containsExactly(
                new com.pokade.domain.admin.metrics.dto.AdminMetricSeriesResponse.Point(1000L, 3.0));
    }

    @Test
    @DisplayName("Prometheus 조회가 예외를 던져도 해당 카드/시리즈만 null·빈 값으로 빠지고 전체는 실패하지 않는다")
    void getDashboard_prometheusUnreachable_doesNotFail() {
        given(prometheusClient.queryScalar(anyString())).willThrow(new RuntimeException("connection refused"));
        given(prometheusClient.queryRange(anyString(), any(Long.class), any(Long.class), anyString()))
                .willThrow(new RuntimeException("connection refused"));

        AdminDashboardResponse response = adminMetricsService.getDashboard();

        assertThat(response.cards()).isNotEmpty();
        assertThat(response.cards()).allSatisfy(card -> assertThat(card.value()).isNull());
        assertThat(response.series()).isNotEmpty();
        assertThat(response.series()).allSatisfy(series -> assertThat(series.points()).isEmpty());
    }

    @Test
    @DisplayName("Prometheus가 empty를 반환하면(데이터 없음) 카드 값은 null이다")
    void getDashboard_noData_returnsNullValue() {
        given(prometheusClient.queryScalar(anyString())).willReturn(Optional.empty());
        given(prometheusClient.queryRange(anyString(), any(Long.class), any(Long.class), anyString()))
                .willReturn(List.of());

        AdminDashboardResponse response = adminMetricsService.getDashboard();

        assertThat(response.cards()).allSatisfy(card -> assertThat(card.value()).isNull());
        assertThat(response.cards())
                .filteredOn(card -> card.key().equals("totalVisits"))
                .singleElement()
                .satisfies(card -> assertThat(card.subValue()).isNull());
    }
}
