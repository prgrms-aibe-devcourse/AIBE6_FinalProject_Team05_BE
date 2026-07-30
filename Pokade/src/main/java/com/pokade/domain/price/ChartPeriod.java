package com.pokade.domain.price;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

public enum ChartPeriod {
    DAYS_30("30d", 30),
    DAYS_90("90d", 90),
    YEAR_1("1y", 365);

    private final String code;
    private final int days;

    ChartPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    public int getDays() {
        return days;
    }

    public static ChartPeriod from(String code) {
        for (ChartPeriod period : values()) {
            if (period.code.equals(code)) {
                return period;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PERIOD);
    }
}
