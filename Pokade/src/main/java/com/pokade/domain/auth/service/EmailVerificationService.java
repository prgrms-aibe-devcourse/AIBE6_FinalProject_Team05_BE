package com.pokade.domain.auth.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.mail.MailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final VerificationCodeStore codeStore;
    private final VerificationCodeGenerator codeGenerator;
    private final MailSender mailSender;

    public void send(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        if(codeStore.isRecentlySent(email)) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        }
        String code = codeGenerator.generate();
        codeStore.save(email, code);
        mailSender.send(email, "[Pokade] 이메일 인증 코드", "인증 코드: " + code + "\n5분 이내 입력");
    }
}
