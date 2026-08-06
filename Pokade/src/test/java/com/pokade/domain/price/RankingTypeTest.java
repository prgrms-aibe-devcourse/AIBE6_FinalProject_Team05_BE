package com.pokade.domain.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

class RankingTypeTest {

    @Test
    @DisplayName("t1 rise, fall 코드는 각각 대응하는 랭킹 타입으로 해석된다")
    void t1() {
        assertThat(RankingType.from("rise")).isEqualTo(RankingType.RISE);
        assertThat(RankingType.from("fall")).isEqualTo(RankingType.FALL);
    }

    @Test
    @DisplayName("t2 정의되지 않은 코드는 INVALID_RANKING_TYPE 예외가 발생한다")
    void t2() {
        assertThatThrownBy(() -> RankingType.from("up"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RANKING_TYPE);
    }

    @Test
    @DisplayName("t3 대소문자가 다르거나 빈 문자열, null이면 INVALID_RANKING_TYPE 예외가 발생한다")
    void t3() {
        assertThatThrownBy(() -> RankingType.from("RISE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RANKING_TYPE);
        assertThatThrownBy(() -> RankingType.from(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RANKING_TYPE);
        assertThatThrownBy(() -> RankingType.from(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RANKING_TYPE);
    }
}
