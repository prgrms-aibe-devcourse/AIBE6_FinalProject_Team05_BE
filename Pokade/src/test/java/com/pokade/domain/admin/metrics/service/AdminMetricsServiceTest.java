package com.pokade.domain.admin.metrics.service;

import com.pokade.domain.admin.metrics.client.PrometheusClient;
import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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

        AdminDashboardResponse response = adminMetricsService.getDashboard("1h");

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

        AdminDashboardResponse response = adminMetricsService.getDashboard("1h");

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

        AdminDashboardResponse response = adminMetricsService.getDashboard("1h");

        assertThat(response.cards()).allSatisfy(card -> assertThat(card.value()).isNull());
        assertThat(response.cards())
                .filteredOn(card -> card.key().equals("totalVisits"))
                .singleElement()
                .satisfies(card -> assertThat(card.subValue()).isNull());
    }

    @Test
    @DisplayName("잘못된 period 값이면 BusinessException(INVALID_PERIOD)을 던진다")
    void getDashboard_invalidPeriod_throws() {
        assertThatThrownBy(() -> adminMetricsService.getDashboard("5m"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("period에 맞는 구간 크기가 시리즈 PromQL의 increase() 윈도우에 그대로 반영된다")
    void getDashboard_periodAffectsSeriesWindow() {
        given(prometheusClient.queryScalar(anyString())).willReturn(Optional.of(1.0));
        given(prometheusClient.queryRange(anyString(), any(Long.class), any(Long.class), anyString()))
                .willReturn(List.of());

        adminMetricsService.getDashboard("1d");

        ArgumentCaptor<String> promqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stepCaptor = ArgumentCaptor.forClass(String.class);
        then(prometheusClient).should(org.mockito.Mockito.atLeastOnce())
                .queryRange(promqlCaptor.capture(), any(Long.class), any(Long.class), stepCaptor.capture());

        assertThat(promqlCaptor.getAllValues()).allSatisfy(promql -> assertThat(promql).contains("[1d]"));
        assertThat(stepCaptor.getAllValues()).allSatisfy(step -> assertThat(step).isEqualTo("1d"));
    }
}
