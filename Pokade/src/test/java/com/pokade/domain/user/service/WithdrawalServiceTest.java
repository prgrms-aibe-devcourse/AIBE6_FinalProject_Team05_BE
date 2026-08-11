package com.pokade.domain.user.service;

import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.event.UserWithdrawalCancelledEvent;
import com.pokade.global.event.UserWithdrawalRequestedEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    RefreshTokenStore refreshTokenStore;
    @Mock
    TokenBlacklistStore tokenBlacklistStore;
    @Mock
    WithdrawalConfirmer withdrawalConfirmer;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @InjectMocks
    WithdrawalService withdrawalService;


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

        withdrawalService.requestWithdrawal(1L, "rawpw", null);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.getWithdrawalRequestedAt()).isNotNull();
        then(eventPublisher).should().publishEvent(any(UserWithdrawalRequestedEvent.class));
    }

    @Test
    @DisplayName("신청: ACTIVE 아니면 WITHDRAWAL_NOT_ALLOWED")
    void requestWithdrawal_notActive() {
        User user = pendingUser();
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(2L, "rawpw", null))
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

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(1L, "wrong", null))
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

    // ===== 확정 배치(오케스트레이션) =====
    // 실제 확정(DB 익명화·이벤트)은 WithdrawalConfirmer 책임 → WithdrawalConfirmerTest에서 검증.
    // 여기서는 스케줄러가 "건별 위임 + 커밋 성공(true)일 때만 Redis 정리 + 실패 격리"를 하는지만 본다.
    @Test
    @DisplayName("확정: 위임이 성공(true)하면 그 유저의 refresh 삭제 + 블랙리스트 등록")
    void confirmExpiredWithdrawals_confirmsThenCleansRedis() {
        User user = pendingUser(); // id=2
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(
                eq(UserStatus.WITHDRAWAL_PENDING), any(LocalDateTime.class)))
                .willReturn(List.of(user));
        given(withdrawalConfirmer.confirm(2L)).willReturn(true);

        withdrawalService.confirmExpiredWithdrawals();

        then(withdrawalConfirmer).should().confirm(2L);
        then(refreshTokenStore).should().delete(2L);
        then(tokenBlacklistStore).should().blacklist(2L);
    }

    @Test
    @DisplayName("확정: 위임이 skip(false)이면 Redis를 건드리지 않는다(그새 철회된 유저 보호)")
    void confirmExpiredWithdrawals_skipsRedisWhenNotConfirmed() {
        User user = pendingUser(); // id=2
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(any(), any()))
                .willReturn(List.of(user));
        given(withdrawalConfirmer.confirm(2L)).willReturn(false);

        withdrawalService.confirmExpiredWithdrawals();

        then(refreshTokenStore).should(never()).delete(any());
        then(tokenBlacklistStore).should(never()).blacklist(any());
    }

    @Test
    @DisplayName("확정: 대상 없으면 위임·Redis 모두 호출하지 않는다")
    void confirmExpiredWithdrawals_empty() {
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(any(), any()))
                .willReturn(List.of());

        withdrawalService.confirmExpiredWithdrawals();

        then(withdrawalConfirmer).should(never()).confirm(any());
        then(refreshTokenStore).should(never()).delete(any());
    }

    @Test
    @DisplayName("확정: 한 건이 실패해도 다음 대상은 계속 확정한다(건별 격리)")
    void confirmExpiredWithdrawals_isolatesFailure() {
        User first = pendingUser();                              // id=2 — 실패할 대상
        User second = pendingUserWithId(3L, "bye2@pokade.com", "바이2"); // id=3 — 계속되어야 함
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(any(), any()))
                .willReturn(List.of(first, second));
        given(withdrawalConfirmer.confirm(2L)).willThrow(new RuntimeException("DB 순단"));
        given(withdrawalConfirmer.confirm(3L)).willReturn(true);

        withdrawalService.confirmExpiredWithdrawals();

        then(withdrawalConfirmer).should().confirm(3L);         // 실패 후에도 다음 대상 처리됨
        then(refreshTokenStore).should(never()).delete(2L);     // 실패 건은 Redis 정리 안 함
        then(refreshTokenStore).should().delete(3L);
        then(tokenBlacklistStore).should().blacklist(3L);
    }

    private User pendingUserWithId(long id, String email, String nickname) {
        User u = User.builder()
                .id(id).email(email).password("ENCODED_PW")
                .nickname(nickname).role(Role.USER).provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE).pointBalance(0)
                .build();
        u.requestWithdrawal(LocalDateTime.now().minusDays(8));
        return u;
    }

    @Test
    @DisplayName("확정 배치: 익명화 토큰 충돌(UNIQUE) 시 새 토큰으로 재시도해 확정한다")
    void confirmExpired_retriesOnTokenCollision() {
        User target = pendingUser(); // id=2, WITHDRAWAL_PENDING
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(eq(UserStatus.WITHDRAWAL_PENDING), any()))
                .willReturn(List.of(target));
        given(withdrawalConfirmer.confirm(2L))
                .willThrow(new DataIntegrityViolationException("unique"))  // 1차: 충돌
                .willReturn(true);                                          // 2차: 성공

        withdrawalService.confirmExpiredWithdrawals();

        then(withdrawalConfirmer).should(times(2)).confirm(2L);   // 재시도 발생
        then(refreshTokenStore).should().delete(2L);              // 최종 정리 수행
        then(tokenBlacklistStore).should().blacklist(2L);
    }

    @Test
    @DisplayName("확정 배치: 재시도 초과 시 건너뛴다(정리 미수행 → 다음 배치 자가치유)")
    void confirmExpired_givesUpAfterMaxRetries() {
        User target = pendingUser();
        given(userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(eq(UserStatus.WITHDRAWAL_PENDING), any()))
                .willReturn(List.of(target));
        given(withdrawalConfirmer.confirm(2L))
                .willThrow(new DataIntegrityViolationException("unique"));  // 항상 충돌

        withdrawalService.confirmExpiredWithdrawals();

        then(withdrawalConfirmer).should(times(3)).confirm(2L);   // MAX_ANON_RETRY 만큼만 시도
        then(refreshTokenStore).should(never()).delete(2L);
    }

    private static final String REAUTH_TOKEN = "reauth.jwt.token";

    private User socialUser() {
        return User.builder()
                .id(3L).email("social@pokade.com").password(null)
                .nickname("소셜").role(Role.USER).provider(Provider.GOOGLE)
                .status(UserStatus.ACTIVE).pointBalance(0)
                .build();
    }

    @Test
    @DisplayName("신청: 소셜 - 유효한 재인증 티켓이면 유예 전환(비번 검증 스킵)")
    void requestWithdrawal_social_success() {
        User social = socialUser();
        given(userRepository.findById(3L)).willReturn(Optional.of(social));
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "purpose")).willReturn("withdrawal_reauth");
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "email")).willReturn("social@pokade.com");
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "provider")).willReturn("GOOGLE");

        withdrawalService.requestWithdrawal(3L, null, REAUTH_TOKEN);

        assertThat(social.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        then(eventPublisher).should().publishEvent(any(UserWithdrawalRequestedEvent.class));
        then(passwordEncoder).should(never()).matches(any(), any());
    }

    @Test
    @DisplayName("신청: 소셜 - 티켓 없음/위조/만료면 INVALID_REAUTH_TICKET")
    void requestWithdrawal_social_invalidTicket() {
        User social = socialUser();
        given(userRepository.findById(3L)).willReturn(Optional.of(social));
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "purpose")).willReturn(null);

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(3L, null, REAUTH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REAUTH_TICKET);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("신청: 소셜 - 티켓 이메일이 유저와 다르면 ACCESS_DENIED")
    void requestWithdrawal_social_emailMismatch() {
        User social = socialUser();
        given(userRepository.findById(3L)).willReturn(Optional.of(social));
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "purpose")).willReturn("withdrawal_reauth");
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "email")).willReturn("attacker@pokade.com");
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "provider")).willReturn("GOOGLE");

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(3L, null, REAUTH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("신청: 소셜 - 티켓 provider가 유저와 다르면 ACCESS_DENIED")
    void requestWithdrawal_social_providerMismatch() {
        User social = socialUser();
        given(userRepository.findById(3L)).willReturn(Optional.of(social));
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "purpose")).willReturn("withdrawal_reauth");
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "email")).willReturn("social@pokade.com");
        given(jwtTokenProvider.parseSignedTicket(REAUTH_TOKEN, "provider")).willReturn("KAKAO");

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(3L, null, REAUTH_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
