package com.pokade.domain.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

class ChartPeriodTest {

    @Test
    @DisplayName("t1 7d, 30d, 90d, 180d 코드는 각각 7일, 30일, 90일, 180일에 대응하는 기간으로 해석된다")
    void t1() {
        assertThat(ChartPeriod.from("7d")).isEqualTo(ChartPeriod.DAYS_7);
        assertThat(ChartPeriod.from("7d").getDays()).isEqualTo(7);
        assertThat(ChartPeriod.from("30d")).isEqualTo(ChartPeriod.DAYS_30);
        assertThat(ChartPeriod.from("30d").getDays()).isEqualTo(30);
        assertThat(ChartPeriod.from("90d")).isEqualTo(ChartPeriod.DAYS_90);
        assertThat(ChartPeriod.from("90d").getDays()).isEqualTo(90);
        assertThat(ChartPeriod.from("180d")).isEqualTo(ChartPeriod.DAYS_180);
        assertThat(ChartPeriod.from("180d").getDays()).isEqualTo(180);
    }

    @Test
    @DisplayName("t2 정의되지 않은 코드는 INVALID_PERIOD 예외가 발생한다")
    void t2() {
        assertThatThrownBy(() -> ChartPeriod.from("1y"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("t3 대소문자가 다르거나 빈 문자열, null이면 INVALID_PERIOD 예외가 발생한다")
    void t3() {
        assertThatThrownBy(() -> ChartPeriod.from("30D"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        assertThatThrownBy(() -> ChartPeriod.from(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        assertThatThrownBy(() -> ChartPeriod.from(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
    }
}
