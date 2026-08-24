package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.WithdrawalCodeStore;
import com.pokade.domain.auth.support.VerificationCodeGenerator;
import com.pokade.domain.auth.support.VerificationMailSender;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawalCodeService {

    private final WithdrawalCodeStore codeStore;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationMailSender verificationMailSender;

    // 본인 이메일로 탈퇴 인증코드를 발송한다(쿨다운 중이면 거부)
    public void send(String email) {
        String code = codeGenerator.generate();
        if (!codeStore.save(email, code)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        }
        verificationMailSender.sendWithdrawalCode(email, code);
    }

    // 탈퇴 인증코드를 원자적으로 검증한다(시도 횟수, 만료, 불일치 -> 성공 시 소모)
    public void verify(String email, String code) {
        switch (codeStore.verifyAndConsume(email, code)) {
            case EXCEEDED -> throw new BusinessException(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
            case EXPIRED -> throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
            case MISMATCH -> throw new BusinessException(ErrorCode.EMAIL_CODE_MISMATCH);
            case OK -> {}
        }
    }
}
