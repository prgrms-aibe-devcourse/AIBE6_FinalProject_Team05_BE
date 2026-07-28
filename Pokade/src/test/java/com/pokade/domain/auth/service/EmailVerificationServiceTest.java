package com.pokade.domain.auth.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.infra.mail.MailSender;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;


@ExtendWith(MockitoExtension.class)
public class EmailVerificationServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    VerificationCodeStore codeStore;
    @Mock
    VerificationCodeGenerator codeGenerator;
    @Mock
    MailSender mailSender;
    @InjectMocks
    EmailVerificationService emailVerificationService;

    private User pendingUser(String email) {
        return User.createLocalUser(email, "ENCODED_PW", "닉네임");
    }

    private User activeUser(String email) {
        return User.builder()
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("정상 요청 시 생성한 인증 코드를 저장소에 저장한다.")
    void send_storesGeneratedCode() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(pendingUser(email)));
        given(codeStore.isRecentlySent(email)).willReturn(false);
        given(codeGenerator.generate()).willReturn("123456");

        emailVerificationService.send(email);

        then(codeStore).should().save(email, "123456");
    }

    @Test
    @DisplayName("60초 내 재요청이면 EMAIL_SEND_RATE_LIMITED 예외를 던지고 저장·생성하지 않는다")
    void send_rejectsWhenRecentlySent() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(pendingUser(email)));
        given(codeStore.isRecentlySent(email)).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_SEND_RATE_LIMITED);

        then(codeGenerator).should(never()).generate();
        then(codeStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 USER_NOT_FOUND 예외를 던지고 저장·생성하지 않는다")
    void send_rejectsWhenUserNotFound() {
        String email = "unknown@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(codeGenerator).should(never()).generate();
        then(codeStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("이미 인증 완료(ACTIVE)된 회원이면 EMAIL_ALREADY_VERIFIED 예외를 던지고 저장·생성하지 않는다")
    void send_rejectsWhenAlreadyVerified() {
        String email = "active@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(activeUser(email)));

        assertThatThrownBy(() -> emailVerificationService.send(email))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_VERIFIED);

        then(codeGenerator).should(never()).generate();
        then(codeStore).should(never()).save(any(), any());
    }

    @Test
    @DisplayName("정상 요청 시 생성한 코드로 인증 메일을 발송한다")
    void send_sendsVerificationEmail() {
        String email = "user@pokade.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.of(pendingUser(email)));
        given(codeStore.isRecentlySent(email)).willReturn(false);
        given(codeGenerator.generate()).willReturn("123456");

        emailVerificationService.send(email);

        then(mailSender).should().send(eq(email), anyString(), contains("123456"));
    }
}