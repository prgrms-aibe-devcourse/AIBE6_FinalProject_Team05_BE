package com.pokade.domain.price;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

// card_prices의 change_*_pct 컬럼에 대응하는 기간 코드. ChartPeriod(30d/90d/1y, 체결 이력 조회용)와는
// 별개 개념이라 값 구성이 다르다 - 혼용하지 않는다.
public enum StatsPeriod {
    DAYS_1("1d"),
    DAYS_7("7d"),
    DAYS_14("14d"),
    DAYS_30("30d"),
    DAYS_90("90d"),
    DAYS_180("180d");

    private final String code;

    StatsPeriod(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static StatsPeriod from(String code) {
        for (StatsPeriod period : values()) {
            if (period.code.equals(code)) {
                return period;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PERIOD);
    }
}
