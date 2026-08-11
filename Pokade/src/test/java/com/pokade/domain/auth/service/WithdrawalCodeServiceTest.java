package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.WithdrawalCodeStore;
import com.pokade.domain.auth.support.VerificationCodeGenerator;
import com.pokade.domain.auth.support.VerificationMailSender;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WithdrawalCodeServiceTest {

    @Mock WithdrawalCodeStore codeStore;
    @Mock VerificationCodeGenerator codeGenerator;
    @Mock VerificationMailSender verificationMailSender;
    @InjectMocks WithdrawalCodeService withdrawalCodeService;

    private static final String EMAIL = "social@pokade.com";

    @Test
    @DisplayName("send: 저장 성공하면 메일 발송")
    void send_success() {
        given(codeGenerator.generate()).willReturn("123456");
        given(codeStore.save(EMAIL, "123456")).willReturn(true);

        withdrawalCodeService.send(EMAIL);

        then(verificationMailSender).should().sendWithdrawalCode(EMAIL, "123456");
    }

    @Test
    @DisplayName("send: 쿨다운 중이면 EMAIL_SEND_RATE_LIMITED, 메일 미발송")
    void send_cooldown() {
        given(codeGenerator.generate()).willReturn("123456");
        given(codeStore.save(EMAIL, "123456")).willReturn(false);

        assertThatThrownBy(() -> withdrawalCodeService.send(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        then(verificationMailSender).should(never()).sendWithdrawalCode(EMAIL, "123456");
    }

    @Test
    @DisplayName("verify: 유효 코드면 통과 + 코드 삭제")
    void verify_success() {
        given(codeStore.getAttemptCount(EMAIL)).willReturn(0L);
        given(codeStore.find(EMAIL)).willReturn(Optional.of("123456"));

        withdrawalCodeService.verify(EMAIL, "123456");

        then(codeStore).should().delete(EMAIL);
    }

    @Test
    @DisplayName("verify: 불일치면 EMAIL_CODE_MISMATCH + 시도 증가")
    void verify_mismatch() {
        given(codeStore.getAttemptCount(EMAIL)).willReturn(0L);
        given(codeStore.find(EMAIL)).willReturn(Optional.of("123456"));

        assertThatThrownBy(() -> withdrawalCodeService.verify(EMAIL, "999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_CODE_MISMATCH);
        then(codeStore).should().incrementAttempt(EMAIL);
        then(codeStore).should(never()).delete(EMAIL);
    }

    @Test
    @DisplayName("verify: 코드 없음/만료면 EMAIL_CODE_EXPIRED")
    void verify_expired() {
        given(codeStore.getAttemptCount(EMAIL)).willReturn(0L);
        given(codeStore.find(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawalCodeService.verify(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_CODE_EXPIRED);
    }

    @Test
    @DisplayName("verify: 시도 5회 이상이면 EMAIL_VERIFY_ATTEMPT_EXCEEDED(코드 조회 안 함)")
    void verify_attemptExceeded() {
        given(codeStore.getAttemptCount(EMAIL)).willReturn(5L);

        assertThatThrownBy(() -> withdrawalCodeService.verify(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
        then(codeStore).should(never()).find(EMAIL);
    }
}