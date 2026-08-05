package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.PasswordResetCodeStore;
import com.pokade.domain.auth.support.VerificationCodeGenerator;
import com.pokade.domain.auth.support.VerificationMailSender;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetCodeStore codeStore;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationMailSender verificationMailSender;
    private final PasswordEncoder passwordEncoder;

    private static final int MAX_RESET_ATTEMPTS = 5;

    // 재설정 코드 발송: ACTIVE + LOCAL 계정만
    public void send(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.isLocalUser()) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String code = codeGenerator.generate();
        if (!codeStore.save(email, code)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        }
        verificationMailSender.sendResetCode(email, code);
    }

    // 코드 검증 후 새 비밀번호로 변경
    @Transactional
    public void confirm(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (codeStore.getAttemptCount(email) >= MAX_RESET_ATTEMPTS) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
        }

        String storeCode = codeStore.find(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED));

        if (!storeCode.equals(code)) {
            codeStore.incrementAttempt(email);
            throw new BusinessException(ErrorCode.EMAIL_CODE_MISMATCH);
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        codeStore.delete(email);
    }
}
