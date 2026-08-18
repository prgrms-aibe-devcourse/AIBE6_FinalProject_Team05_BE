package com.pokade.domain.admin.metrics.client;

import java.util.List;
import java.util.Optional;

public interface PrometheusClient {

    // 순간값 조회(/api/v1/query) - 결과 벡터가 비어있으면(아직 한 번도 발생 안 한 지표 등) empty.
    Optional<Double> queryScalar(String promql);

    // 구간 조회(/api/v1/query_range) - 시계열 차트용.
    List<PrometheusPoint> queryRange(String promql, long startEpochSeconds, long endEpochSeconds, String step);

    record PrometheusPoint(long epochSeconds, double value) {
    }
}
