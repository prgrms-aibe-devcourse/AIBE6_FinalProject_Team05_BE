package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserAccessGuardTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserAccessGuard userAccessGuard;

    private User userWith(UserStatus status) {
        return User.builder()
                .id(1L).email("user@pokade.com").password("ENCODED_PW")
                .nickname("지우").role(Role.USER).provider(Provider.LOCAL)
                .status(status).pointBalance(0)
                .build();
    }

    @Test
    @DisplayName("ACTIVE면 통과")
    void active_ok() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWith(UserStatus.ACTIVE)));

        assertThatCode(() -> userAccessGuard.assertWritable(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유예중(WITHDRAWAL_PENDING)이면 ACCOUNT_NOT_ACTIVE")
    void pending_blocked() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWith(UserStatus.WITHDRAWAL_PENDING)));

        assertThatThrownBy(() -> userAccessGuard.assertWritable(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("정지(SUSPENDED)면 ACCOUNT_NOT_ACTIVE")
    void suspended_blocked() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWith(UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> userAccessGuard.assertWritable(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("유저 없으면 USER_NOT_FOUND")
    void notFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userAccessGuard.assertWritable(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
