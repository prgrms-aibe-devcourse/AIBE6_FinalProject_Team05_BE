package com.pokade.domain.admin.metrics.dto;

import java.util.List;

// 시간에 따른 추이를 보여주는 지표 하나. 같은 group의 시리즈끼리는 스케일이 맞아 한 차트에 겹쳐 그릴 수 있다.
public record AdminMetricSeriesResponse(String key, String label, String unit, String group, List<Point> points) {

    public record Point(long epochSeconds, double value) {
    }
}
