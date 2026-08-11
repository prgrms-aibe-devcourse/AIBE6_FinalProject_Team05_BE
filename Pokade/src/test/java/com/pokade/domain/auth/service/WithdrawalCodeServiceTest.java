package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.VerificationResult;
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
    @DisplayName("verify: OK면 통과(원자 검증·소모 위임)")
    void verify_success() {
        given(codeStore.verifyAndConsume(EMAIL, "123456")).willReturn(VerificationResult.OK);

        withdrawalCodeService.verify(EMAIL, "123456");

        then(codeStore).should().verifyAndConsume(EMAIL, "123456");
    }

    @Test
    @DisplayName("verify: MISMATCH면 EMAIL_CODE_MISMATCH")
    void verify_mismatch() {
        given(codeStore.verifyAndConsume(EMAIL, "999999")).willReturn(VerificationResult.MISMATCH);

        assertThatThrownBy(() -> withdrawalCodeService.verify(EMAIL, "999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_CODE_MISMATCH);
    }

    @Test
    @DisplayName("verify: EXPIRED면 EMAIL_CODE_EXPIRED")
    void verify_expired() {
        given(codeStore.verifyAndConsume(EMAIL, "123456")).willReturn(VerificationResult.EXPIRED);

        assertThatThrownBy(() -> withdrawalCodeService.verify(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_CODE_EXPIRED);
    }

    @Test
    @DisplayName("verify: EXCEEDED면 EMAIL_VERIFY_ATTEMPT_EXCEEDED")
    void verify_attemptExceeded() {
        given(codeStore.verifyAndConsume(EMAIL, "123456")).willReturn(VerificationResult.EXCEEDED);

        assertThatThrownBy(() -> withdrawalCodeService.verify(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
    }
}