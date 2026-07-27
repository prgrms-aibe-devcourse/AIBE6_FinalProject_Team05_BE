package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    @InjectMocks
    AuthService authService;

    private SignupRequest request(String email, String pw, String nickname) {
        return new SignupRequest(email, pw, nickname);
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
}