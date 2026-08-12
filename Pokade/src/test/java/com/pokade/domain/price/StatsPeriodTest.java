package com.pokade.domain.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

class StatsPeriodTest {

    @Test
    @DisplayName("t1 1d/7d/14d/30d/90d/180d 코드는 각각 대응하는 기간으로 해석된다")
    void t1() {
        assertThat(StatsPeriod.from("1d")).isEqualTo(StatsPeriod.DAYS_1);
        assertThat(StatsPeriod.from("7d")).isEqualTo(StatsPeriod.DAYS_7);
        assertThat(StatsPeriod.from("14d")).isEqualTo(StatsPeriod.DAYS_14);
        assertThat(StatsPeriod.from("30d")).isEqualTo(StatsPeriod.DAYS_30);
        assertThat(StatsPeriod.from("90d")).isEqualTo(StatsPeriod.DAYS_90);
        assertThat(StatsPeriod.from("180d")).isEqualTo(StatsPeriod.DAYS_180);
    }

    @Test
    @DisplayName("t2 정의되지 않은 코드는 INVALID_PERIOD 예외가 발생한다")
    void t2() {
        assertThatThrownBy(() -> StatsPeriod.from("1y"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("t3 대소문자가 다르거나 빈 문자열, null이면 INVALID_PERIOD 예외가 발생한다")
    void t3() {
        assertThatThrownBy(() -> StatsPeriod.from("7D"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        assertThatThrownBy(() -> StatsPeriod.from(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
        assertThatThrownBy(() -> StatsPeriod.from(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PERIOD);
    }
}
