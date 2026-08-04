package com.pokade.domain.auth.service;

import com.pokade.global.infra.mail.MailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationMailSender {

    private final MailSender mailSender;

    // 인증 코드 메일을 비동기로 발송한다.
    @Async
    public void sendCode(String email, String code) {
        mailSender.send(email, "[Pokade] 이메일 인증 코드", "인증 코드: " + code + "\n5분 이내 입력");
    }
}
