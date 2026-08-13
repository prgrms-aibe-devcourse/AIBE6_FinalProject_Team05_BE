package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.auth.store.LoginAttemptStore;
import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RefreshTokenStore refreshTokenStore;
    @Mock
    LoginAttemptStore loginAttemptStore;
    @InjectMocks
    AuthService authService;

    private SignupRequest request(String email, String pw, String nickname) {
        return new SignupRequest(email, pw, nickname);
    }

    private User userWithStatus(String email, UserStatus status) {
        return User.builder()
                .id(1L)
                .email(email)
                .password("ENCODED_PW")
                .role(Role.USER)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("정상 가입 시 비밀번호를 암호화해 PENDING 상태로 저장하고 응답을 반환한다")
    void signup_success() {
        // given
        SignupRequest req = request("test@pokade.com", "pokade1234", "홍길동");
        given(userRepository.findByEmail(req.email())).willReturn(Optional.empty());
        given(userRepository.existsByNickname(req.nickname())).willReturn(false);
        given(passwordEncoder.encode(req.password())).willReturn("ENCODED_PW");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        SignupResponse res = authService.signup(req);

        // then
        assertThat(res.email()).isEqualTo("test@pokade.com");
        assertThat(res.nickname()).isEqualTo("홍길동");
        assertThat(res.status()).isEqualTo(UserStatus.PENDING);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("ENCODED_PW"); // 평문 저장 아님
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("이미 활성(ACTIVE) 계정 이메일이면 DUPLICATE_EMAIL 예외를 던지고 저장하지 않는다")
    void signup_duplicateEmail() {
        SignupRequest req = request("dup@pokade.com", "pokade1234", "홍길동");
        given(userRepository.findByEmail(req.email()))
                .willReturn(Optional.of(userWithStatus("dup@pokade.com", UserStatus.ACTIVE)));

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("미인증(PENDING) 계정 이메일이면 EMAIL_NOT_VERIFIED 예외를 던지고 저장하지 않는다")
    void signup_pendingEmail() {
        SignupRequest req = request("pending@pokade.com", "pokade1234", "홍길동");
        given(userRepository.findByEmail(req.email()))
                .willReturn(Optional.of(userWithStatus("pending@pokade.com", UserStatus.PENDING)));

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("닉네임이 중복되면 DUPLICATE_NICKNAME 예외를 던진다")
    void signup_duplicateNickname() {
        SignupRequest req = request("new@pokade.com", "pokade1234", "중복닉");
        given(userRepository.findByEmail(req.email())).willReturn(Optional.empty());
        given(userRepository.existsByNickname(req.nickname())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("정상 로그인 시 access·refresh 토큰을 발급하고 refresh를 저장한 뒤 TokenPair를 반환한다")
    void login_success() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(userWithStatus(email, UserStatus.ACTIVE)));
        given(passwordEncoder.matches("pokade1234", "ENCODED_PW")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("refresh-token");

        TokenPair result = authService.login(new LoginRequest(email, "pokade1234"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        then(refreshTokenStore).should().save(eq(1L), any(), eq("refresh-token"));
        then(loginAttemptStore).should().reset(email);          // 성공 → 카운터 리셋
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 LOGIN_FAILED 예외를 던지고 토큰을 발급·저장하지 않는다")
    void login_wrongPassword() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(userWithStatus(email, UserStatus.ACTIVE)));
        given(passwordEncoder.matches("wrong", "ENCODED_PW")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);

        then(refreshTokenStore).should(never()).save(any(), any());
        then(loginAttemptStore).should().recordFailure(email);  // 실패 → 카운트
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 더미 BCrypt 비교로 응답시간을 맞추고 LOGIN_FAILED를 던진다")
    void login_userNotFound() {
        String email = "unknown@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "pokade1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);

        then(passwordEncoder).should().matches(any(), any());   // ← 추가: 유저 없어도 더미 비교 호출
        then(refreshTokenStore).should(never()).save(any(), any());
        then(loginAttemptStore).should().recordFailure(email);  // 실패 → 카운트
    }

    @Test
    @DisplayName("이메일 미인증(PENDING) 회원이면 EMAIL_NOT_VERIFIED 예외를 던진다")
    void login_notVerified() {
        String email = "pending@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(userWithStatus(email, UserStatus.PENDING)));
        given(passwordEncoder.matches("pokade1234", "ENCODED_PW")).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "pokade1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        then(refreshTokenStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("정지(SUSPENDED) 회원이면 ACCOUNT_SUSPENDED 예외를 던지고 토큰을 발급·저장하지 않는다")
    void login_suspended() {
        String email = "suspended@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(userWithStatus(email, UserStatus.SUSPENDED)));
        given(passwordEncoder.matches("pokade1234", "ENCODED_PW")).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "pokade1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        then(refreshTokenStore).should(never()).save(any(), any());
        then(loginAttemptStore).should(never()).reset(email);
    }

    @Test
    @DisplayName("탈퇴 확정(DELETED) 회원이면 계정 존재를 숨기려 LOGIN_FAILED 예외를 던지고 토큰을 발급·저장하지 않는다")
    void login_deleted() {
        String email = "deleted@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(userWithStatus(email, UserStatus.DELETED)));
        given(passwordEncoder.matches("pokade1234", "ENCODED_PW")).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "pokade1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);

        then(refreshTokenStore).should(never()).save(any(), any());
        then(loginAttemptStore).should(never()).reset(email);
    }

    @Test
    @DisplayName("탈퇴 유예(WITHDRAWAL_PENDING) 회원은 철회를 위해 로그인이 허용되어 토큰을 발급받는다")
    void login_withdrawalPending_allowed() {
        String email = "bye@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(userWithStatus(email, UserStatus.WITHDRAWAL_PENDING)));
        given(passwordEncoder.matches("pokade1234", "ENCODED_PW")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("refresh-token");

        TokenPair result = authService.login(new LoginRequest(email, "pokade1234"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        then(refreshTokenStore).should().save(eq(1L), any(), eq("refresh-token"));
        then(loginAttemptStore).should().reset(email);
    }

    @Test
    @DisplayName("저장된 refresh와 일치하면 원자 회전(compareAndRotate)하고 새 access+새 refresh를 반환한다")
    void reissue_success() {
        String oldRefresh = "old-refresh";
        given(jwtTokenProvider.isValid(oldRefresh)).willReturn(true);
        given(jwtTokenProvider.getUserId(oldRefresh)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(oldRefresh)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("new-refresh");
        given(refreshTokenStore.compareAndRotate(1L, "A", oldRefresh, "new-refresh")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithStatus("user@pokade.com", UserStatus.ACTIVE)));
        given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("new-access");

        TokenPair result = authService.reissue(oldRefresh);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        then(refreshTokenStore).should().compareAndRotate(1L, "A", oldRefresh, "new-refresh");
    }

    @Test
    @DisplayName("refresh 서명·만료가 유효하지 않으면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void reissue_invalidToken() {
        given(jwtTokenProvider.isValid("bad")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue("bad"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("저장된 refresh가 없으면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void reissue_noStoredToken() {
        given(jwtTokenProvider.isValid("r")).willReturn(true);
        given(jwtTokenProvider.getUserId("r")).willReturn(1L);
        given(jwtTokenProvider.getSessionId("r")).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue("r"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("같은 세션 내 저장값·grace 모두와 불일치하면 TOKEN_STOLEN을 던지고 그 세션만 삭제한다(deleteAll 아님)")
    void reissue_stolen() {
        String presented = "stale-refresh";
        given(jwtTokenProvider.isValid(presented)).willReturn(true);
        given(jwtTokenProvider.getUserId(presented)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(presented)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithStatus("user@pokade.com", UserStatus.ACTIVE)));
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("unused-new");
        given(refreshTokenStore.compareAndRotate(1L, "A", presented, "unused-new")).willReturn(false);
        given(refreshTokenStore.matchesGrace(1L, "A", presented)).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(presented))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_STOLEN);

        then(refreshTokenStore).should().delete(1L, "A");            // 그 세션만
        then(refreshTokenStore).should(never()).deleteAll(any());    // 다른 세션 생존
    }

    @Test
    @DisplayName("저장값과 불일치해도 grace와 일치하면 재회전 없이 새 access만 발급하고 refresh는 재세팅하지 않는다")
    void reissue_graceConverges() {
        String presented = "old-refresh";
        given(jwtTokenProvider.isValid(presented)).willReturn(true);
        given(jwtTokenProvider.getUserId(presented)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(presented)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("unused-new");
        given(refreshTokenStore.compareAndRotate(1L, "A", presented, "unused-new")).willReturn(false);
        given(refreshTokenStore.matchesGrace(1L, "A", presented)).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithStatus("user@pokade.com", UserStatus.ACTIVE)));
        given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("new-access");

        TokenPair result = authService.reissue(presented);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isNull(); // Option Y: grace 수렴은 새 access만, refresh 없음
        then(refreshTokenStore).should(never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("refresh는 유효하나 유저가 존재하지 않으면 INVALID_REFRESH_TOKEN 예외를 던지고 전 세션을 삭제한다")
    void reissue_userNotFound() {
        String presented = "current-refresh";
        given(jwtTokenProvider.isValid(presented)).willReturn(true);
        given(jwtTokenProvider.getUserId(presented)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(presented)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(presented))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        then(refreshTokenStore).should().deleteAll(1L);
    }

    @Test
    @DisplayName("정지(SUSPENDED) 회원이면 재발급을 거부하고(INVALID) 전 세션을 폐기한다")
    void reissue_suspended_rejectedAndPurged() {
        String presented = "current-refresh";
        given(jwtTokenProvider.isValid(presented)).willReturn(true);
        given(jwtTokenProvider.getUserId(presented)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(presented)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithStatus("s@pokade.com", UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> authService.reissue(presented))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        then(refreshTokenStore).should().deleteAll(1L);            // 계정 전체 세션 폐기
        then(refreshTokenStore).should(never()).compareAndRotate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴 확정(DELETED) 회원이면 재발급을 거부하고(INVALID) 전 세션을 폐기한다(배치 Redis 정리 유실 자가치유)")
    void reissue_deleted_rejectedAndPurged() {
        String presented = "current-refresh";
        given(jwtTokenProvider.isValid(presented)).willReturn(true);
        given(jwtTokenProvider.getUserId(presented)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(presented)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithStatus("d@pokade.com", UserStatus.DELETED)));

        assertThatThrownBy(() -> authService.reissue(presented))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        then(refreshTokenStore).should().deleteAll(1L);
        then(refreshTokenStore).should(never()).compareAndRotate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴 유예(WITHDRAWAL_PENDING) 회원은 재발급이 허용된다(유예 중 세션 유지)")
    void reissue_withdrawalPending_allowed() {
        String presented = "old-refresh";
        given(jwtTokenProvider.isValid(presented)).willReturn(true);
        given(jwtTokenProvider.getUserId(presented)).willReturn(1L);
        given(jwtTokenProvider.getSessionId(presented)).willReturn("A");
        given(refreshTokenStore.exists(1L, "A")).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithStatus("bye@pokade.com", UserStatus.WITHDRAWAL_PENDING)));
        given(jwtTokenProvider.createRefreshToken(any(), any())).willReturn("new-refresh");
        given(refreshTokenStore.compareAndRotate(1L, "A", presented, "new-refresh")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("new-access");

        TokenPair result = authService.reissue(presented);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("로그아웃하면 refresh의 userId·sid로 그 세션 토큰을 삭제한다")
    void logout_deletesTokens() {
        given(jwtTokenProvider.getUserId("refresh")).willReturn(1L);
        given(jwtTokenProvider.getSessionId("refresh")).willReturn("A");

        authService.logout("refresh");

        then(refreshTokenStore).should().delete(1L, "A");
    }

    @Test
    @DisplayName("refresh가 무효·만료여서 userId를 못 구하면 아무것도 삭제하지 않고 예외 없이 넘어간다(멱등)")
    void logout_idempotentWhenInvalid() {
        given(jwtTokenProvider.getUserId("bad")).willReturn(null);

        authService.logout("bad");

        then(refreshTokenStore).should(never()).delete(any(), any());
    }

    @Test
    @DisplayName("시도 초과로 차단된 이메일이면 LOGIN_ATTEMPTS_EXCEEDED를 던지고 유저 조회·비번 검증을 하지 않는다")
    void login_blocked() {
        String email = "user@pokade.com";
        given(loginAttemptStore.isBlocked(email)).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "pokade1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);

        then(userRepository).should(never()).findByEmail(any());   // BCrypt 전에 차단
        then(passwordEncoder).should(never()).matches(any(), any());
    }
}
