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

    private static final int MAX_VERIFY_ATTEMPTS = 5;

    // 본인 이메일로 탈퇴 인증코드를 발송한다(쿨다운 중이면 거부)
    public void send(String email) {
        String code = codeGenerator.generate();
        if (!codeStore.save(email, code)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        }
        verificationMailSender.sendWithdrawalCode(email, code);
    }

    // 탈퇴 인증코드를 검증한다(시도 횟수, 만료, 불일치 -> 성공 시 소모)
    public void verify(String email, String code) {
        if (codeStore.getAttemptCount(email) >= MAX_VERIFY_ATTEMPTS) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
        }
        String storedCode = codeStore.find(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED));
        if (!storedCode.equals(code)) {
            codeStore.incrementAttempt(email);
            throw new BusinessException(ErrorCode.EMAIL_CODE_MISMATCH);
        }
        codeStore.delete(email); // 성공 시 소모
    }
}
