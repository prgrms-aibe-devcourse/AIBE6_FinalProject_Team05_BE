package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
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
        given(userRepository.existsByEmail(req.email())).willReturn(false);
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
    @DisplayName("이메일이 중복되면 DUPLICATE_EMAIL 예외를 던지고 저장하지 않는다")
    void signup_duplicateEmail() {
        SignupRequest req = request("dup@pokade.com", "pokade1234", "홍길동");
        given(userRepository.existsByEmail(req.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("닉네임이 중복되면 DUPLICATE_NICKNAME 예외를 던진다")
    void signup_duplicateNickname() {
        SignupRequest req = request("new@pokade.com", "pokade1234", "중복닉");
        given(userRepository.existsByEmail(req.email())).willReturn(false);
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
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");

        TokenPair result = authService.login(new LoginRequest(email, "pokade1234"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        then(refreshTokenStore).should().save(1L, "refresh-token");
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
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 LOGIN_FAILED 예외를 던진다")
    void login_userNotFound() {
        String email = "unknown@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "pokade1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);

        then(refreshTokenStore).should(never()).save(any(), any());
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
}
