package com.pokade.domain.admin.metrics.client.dto;

import java.util.List;
import java.util.Map;

// instant query는 value, range query는 values를 채워 보낸다 - 하나의 타입으로 공유해서 매핑한다.
public record PrometheusQueryResponse(String status, Data data) {

    public record Data(String resultType, List<Result> result) {
    }

    public record Result(Map<String, String> metric, List<Object> value, List<List<Object>> values) {
    }
}
