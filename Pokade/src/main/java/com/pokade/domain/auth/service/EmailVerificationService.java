package com.pokade.domain.auth.service;

import com.pokade.domain.auth.store.VerificationCodeStore;
import com.pokade.domain.auth.support.VerificationCodeGenerator;
import com.pokade.domain.auth.support.VerificationMailSender;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final VerificationCodeStore codeStore;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationMailSender verificationMailSender;

    public void send(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        String code = codeGenerator.generate();
        if (!codeStore.save(email, code)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        }
        verificationMailSender.sendCode(email, code);
    }

    @Transactional
    public void verify(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        switch (codeStore.verifyAndConsume(email, code)) {
            case EXCEEDED -> throw new BusinessException(ErrorCode.EMAIL_VERIFY_ATTEMPT_EXCEEDED);
            case EXPIRED -> throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
            case MISMATCH -> throw new BusinessException(ErrorCode.EMAIL_CODE_MISMATCH);
            case OK -> user.verifyEmail();
        }
    }
}
