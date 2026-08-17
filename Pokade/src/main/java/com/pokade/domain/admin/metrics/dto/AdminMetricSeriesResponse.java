package com.pokade.domain.admin.metrics.dto;

import java.util.List;

// 시간에 따른 추이를 보여주는 지표 차트 하나.
public record AdminMetricSeriesResponse(String key, String label, String unit, List<Point> points) {

    public record Point(long epochSeconds, double value) {
    }
}
