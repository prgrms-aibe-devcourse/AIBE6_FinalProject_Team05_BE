package com.pokade.domain.user.entity;

import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private User user(int pointBalance) {
        return User.builder()
                .email("point-user@test.com")
                .nickname("tester")
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE)
                .pointBalance(pointBalance)
                .build();
    }

    @Test
    @DisplayName("chargePoints: 기존 잔액에 충전 금액을 더한다")
    void chargePoints_addsToBalance() {
        User user = user(1000);

        user.chargePoints(5000);

        assertThat(user.getPointBalance()).isEqualTo(6000);
    }

    @Test
    @DisplayName("deductPoints: 잔액이 충분하면 차감하고 남은 잔액을 반영한다")
    void deductPoints_sufficientBalance_deducts() {
        User user = user(10000);

        user.deductPoints(3000);

        assertThat(user.getPointBalance()).isEqualTo(7000);
    }

    @Test
    @DisplayName("deductPoints: 잔액과 정확히 같은 금액은 차감할 수 있다 (0원까지 허용)")
    void deductPoints_exactBalance_deductsToZero() {
        User user = user(3000);

        user.deductPoints(3000);

        assertThat(user.getPointBalance()).isZero();
    }

    @Test
    @DisplayName("deductPoints: 잔액이 부족하면 INSUFFICIENT_POINT_BALANCE를 던지고 잔액은 그대로다")
    void deductPoints_insufficientBalance_throws() {
        User user = user(1000);

        assertThatThrownBy(() -> user.deductPoints(2000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_POINT_BALANCE);
        assertThat(user.getPointBalance()).isEqualTo(1000);
    }
}
