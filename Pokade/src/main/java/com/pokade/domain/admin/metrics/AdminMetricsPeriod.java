package com.pokade.domain.admin.metrics;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

// 차트 조회 단위 - lookbackHours(전체 조회 범위)와 step(PromQL 구간 크기)이 세트로 묶여있다.
// 1일 단위인데 조회 범위가 24시간이면 점이 1개뿐이라 의미가 없으므로, 단위가 굵어질수록 범위도 늘린다.
public enum AdminMetricsPeriod {
    MINUTES_10("10m", 1, "10m"),
    HOUR_1("1h", 6, "1h"),
    DAY_1("1d", 24 * 7, "1d");

    private final String code;
    private final long lookbackHours;
    private final String step;

    AdminMetricsPeriod(String code, long lookbackHours, String step) {
        this.code = code;
        this.lookbackHours = lookbackHours;
        this.step = step;
    }

    public long getLookbackHours() {
        return lookbackHours;
    }

    public String getStep() {
        return step;
    }

    public static AdminMetricsPeriod from(String code) {
        for (AdminMetricsPeriod period : values()) {
            if (period.code.equals(code)) {
                return period;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PERIOD);
    }
}
