package com.pokade.domain.user.service;

import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.event.UserWithdrawalCancelledEvent;
import com.pokade.global.event.UserWithdrawalRequestedEvent;
import com.pokade.global.event.UserWithdrawnEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.TokenBlacklistStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock TokenBlacklistStore tokenBlacklistStore;
    @InjectMocks WithdrawalService withdrawalService;

    private User activeUser() {
        return User.builder()
                .id(1L).email("user@pokade.com").password("ENCODED_PW")
                .nickname("지우").role(Role.USER).provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE).pointBalance(0)
                .build();
    }

    private User pendingUser() {
        User u = User.builder()
                .id(2L).email("bye@pokade.com").password("ENCODED_PW")
                .nickname("바이").role(Role.USER).provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE).pointBalance(0)
                .build();
        u.requestWithdrawal(LocalDateTime.now().minusDays(8)); // ACTIVE -> WITHDRAWAL_PENDING
        return u;
    }

    // ===== 신청 =====
    @Test
    @DisplayName("신청: ACTIVE + 올바른 비번이면 유예 전환 + 이벤트 발행")
    void requestWithdrawal_success() {
        User user = activeUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("rawpw", "ENCODED_PW")).willReturn(true);

        withdrawalService.requestWithdrawal(1L, "rawpw");

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalRequestedAt()).isNotNull();
        then(eventPublisher).should().publishEvent(any(UserWithdrawalRequestedEvent.class));
    }

    @Test
    @DisplayName("신청: ACTIVE 아니면 WITHDRAWAL_NOT_ALLOWED")
    void requestWithdrawal_notActive() {
        User user = pendingUser();
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(2L, "rawpw"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_NOT_ALLOWED);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("신청: 비번 불일치면 INVALID_CURRENT_PASSWORD")
    void requestWithdrawal_wrongPassword() {
        User user = activeUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "ENCODED_PW")).willReturn(false);

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    // ===== 철회 =====
    @Test
    @DisplayName("철회: 유예 상태면 ACTIVE 복구 + 이벤트 발행")
    void cancelWithdrawal_success() {
        User user = pendingUser();
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        withdrawalService.cancelWithdrawal(2L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getWithdrawalRequestedAt()).isNull();
        then(eventPublisher).should().publishEvent(any(UserWithdrawalCancelledEvent.class));
    }

    @Test
    @DisplayName("철회: 유예 상태 아니면 NOT_WITHDRAWAL_PENDING")
    void cancelWithdrawal_notPending() {
        User user = activeUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> withdrawalService.cancelWithdrawal(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_WITHDRAWAL_PENDING);
    }

    // ===== 확정 배치 =====
    @Test
    @DisplayName("확정: 유예 만료분을 DELETED 익명화 + 토큰 무효화·블랙리스트·이벤트")
    void confirmExpiredWithdrawals_confirms() {
        User user = pendingUser();
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(
                eq(UserStatus.WITHDRAWAL_PENDING), any(LocalDateTime.class)))
                .willReturn(List.of(user));

        withdrawalService.confirmExpiredWithdrawals();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("deleted_2@pokade.invalid");
        assertThat(user.getNickname()).isEqualTo("deleted_2");
        assertThat(user.getPassword()).isNull();
        then(refreshTokenStore).should().delete(2L);
        then(tokenBlacklistStore).should().blacklist(2L);
        then(eventPublisher).should().publishEvent(any(UserWithdrawnEvent.class));
    }

    @Test
    @DisplayName("확정: 대상 없으면 아무 것도 안 함")
    void confirmExpiredWithdrawals_empty() {
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(any(), any()))
                .willReturn(List.of());

        withdrawalService.confirmExpiredWithdrawals();

        then(refreshTokenStore).should(never()).delete(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }
}
