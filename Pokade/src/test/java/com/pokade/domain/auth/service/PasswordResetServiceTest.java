package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.PasswordResetCodeStore;
import com.pokade.domain.auth.store.VerificationResult;
import com.pokade.domain.auth.support.VerificationCodeGenerator;
import com.pokade.domain.auth.support.VerificationMailSender;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock
    PasswordResetCodeStore codeStore;
    @Mock
    VerificationCodeGenerator codeGenerator;
    @Mock
    VerificationMailSender verificationMailSender;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks PasswordResetService passwordResetService;

    private User activeLocalUser(String email) {
        return User.builder()
                .email(email)
                .password("ENCODED_PW")
                .status(UserStatus.ACTIVE)
                .provider(Provider.LOCAL)
                .build();
    }

    private User socialUser(String email) {
        return User.builder()
                .email(email)
                .status(UserStatus.ACTIVE)
                .provider(Provider.KAKAO)
                .build();
    }

    private User pendingUser(String email) {
        return User.createLocalUser(email, "ENCODED_PW", "닉네임"); // PENDING + LOCAL
    }

    // ===== send =====

    @Test
    @DisplayName("정상 요청 시 생성한 재설정 코드를 저장소에 저장한다")
    void send_storesGeneratedCode() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeLocalUser(email)));
        given(codeGenerator.generate()).willReturn("123456");
        given(codeStore.save(email, "123456")).willReturn(true);

        passwordResetService.send(email);

        then(codeStore).should().save(email, "123456");
    }

    @Test
    @DisplayName("정상 요청 시 생성한 코드로 재설정 메일을 발송한다")
    void send_sendsResetEmail() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeLocalUser(email)));
        given(codeGenerator.generate()).willReturn("123456");
        given(codeStore.save(email, "123456")).willReturn(true);

        passwordResetService.send(email);

        then(verificationMailSender).should().sendResetCode(email, "123456");
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 USER_NOT_FOUND 예외를 던지고 저장·생성하지 않는다")
    void send_rejectsWhenUserNotFound() {
        String email = "unknown@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(codeGenerator).should(never()).generate();
        then(codeStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("소셜(비-LOCAL) 계정이면 PASSWORD_CHANGE_NOT_ALLOWED 예외를 던지고 저장·생성하지 않는다")
    void send_rejectsWhenSocialAccount() {
        String email = "kakao@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(socialUser(email)));

        assertThatThrownBy(() -> passwordResetService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);

        then(codeGenerator).should(never()).generate();
        then(codeStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("아직 인증 안 된(PENDING) 계정이면 EMAIL_NOT_VERIFIED 예외를 던지고 저장·생성하지 않는다")
    void send_rejectsWhenNotActive() {
        String email = "pending@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(pendingUser(email)));

        assertThatThrownBy(() -> passwordResetService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        then(codeGenerator).should(never()).generate();
        then(codeStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("쿨다운 선점 실패(동시 요청·60초 내 재요청)면 EMAIL_SEND_RATE_LIMITED 예외를 던지고 메일을 발송하지 않는다")
    void send_rejectsWhenCooldownActive() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeLocalUser(email)));
        given(codeGenerator.generate()).willReturn("123456");
        given(codeStore.save(email, "123456")).willReturn(false);

        assertThatThrownBy(() -> passwordResetService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_SEND_RATE_LIMITED);

        then(verificationMailSender).should(never()).sendResetCode(any(), any());
    }

    // ===== confirm =====

    @Test
    @DisplayName("코드가 일치(OK)하면 새 비밀번호로 변경한다")
    void confirm_changesPasswordOnOk() {
        String email = "user@pokade.com";
        User user = activeLocalUser(email);
        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
        given(codeStore.verifyAndConsume(email, "123456")).willReturn(VerificationResult.OK);
        given(passwordEncoder.encode("newPass123")).willReturn("ENCODED_NEW");

        passwordResetService.confirm(email, "123456", "newPass123");

        assertThat(user.getPassword()).isEqualTo("ENCODED_NEW");
        then(codeStore).should().verifyAndConsume(email, "123456");
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 USER_NOT_FOUND 예외를 던지고 코드 검증을 하지 않는다")
    void confirm_rejectsWhenUserNotFound() {
        String email = "unknown@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.confirm(email, "123456", "newPass123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(codeStore).should(never()).verifyAndConsume(any(), any());
    }

    @Test
    @DisplayName("시도 초과(EXCEEDED)면 EMAIL_VERIFY_ATTEMPT_EXCEEDED 예외를 던진다")
    void confirm_rejectsWhenAttemptExceeded() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeLocalUser(email)));
        given(codeStore.verifyAndConsume(email, "123456")).willReturn(VerificationResult.EXCEEDED);

        assertThatThrownBy(() -> passwordResetService.confirm(email, "123456", "newPass123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
    }

    @Test
    @DisplayName("코드 저장이 없으면(EXPIRED) EMAIL_CODE_EXPIRED 예외를 던진다")
    void confirm_rejectsWhenCodeExpired() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeLocalUser(email)));
        given(codeStore.verifyAndConsume(email, "123456")).willReturn(VerificationResult.EXPIRED);

        assertThatThrownBy(() -> passwordResetService.confirm(email, "123456", "newPass123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_CODE_EXPIRED);
    }

    @Test
    @DisplayName("코드가 다르면(MISMATCH) EMAIL_CODE_MISMATCH 예외를 던지고 비번을 바꾸지 않는다")
    void confirm_rejectsWhenCodeMismatch() {
        String email = "user@pokade.com";
        User user = activeLocalUser(email);
        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
        given(codeStore.verifyAndConsume(email, "123456")).willReturn(VerificationResult.MISMATCH);

        assertThatThrownBy(() -> passwordResetService.confirm(email, "123456", "newPass123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_CODE_MISMATCH);

        assertThat(user.getPassword()).isEqualTo("ENCODED_PW"); // 그대로
    }
}