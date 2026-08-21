package com.pokade.domain.auth.support;

import com.pokade.global.infra.mail.MailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationMailSender {

    // 메일 문구에 노출하는 코드 유효시간 - 실제 만료는 각  CodeStore의 CODE_TTL에 의해 결정된다.
    private static final int CODE_TTL_MINUTES = 5;

    private final MailSender mailSender;

    // 인증 코드 메일을 비동기로 발송한다.
    @Async
    public void sendCode(String email, String code) {
        mailSender.send(email, "[Pokade] 이메일 인증 코드",
                VerificationMailTemplate.codeMail(
                        "이메일 인증 코드",
                        "아래 코드를 입력하시면 이메일 인증이 완료됩니다.",
                        code, CODE_TTL_MINUTES));
    }

    // 비밀번호 재설정 코드 메일을 비동기로 발송한다.
    @Async
    public void sendResetCode(String email, String code) {
        mailSender.send(email, "[Pokade] 비밀번호 재설정 코드",
                VerificationMailTemplate.codeMail(
                        "비밀번호 재설정 코드",
                        "아래 코드를 입력하시면 새 비밀번호를 설정할 수 있습니다.",
                        code, CODE_TTL_MINUTES));
    }

    // 탈퇴 인증 코드 메일을 보낸다
    @Async
    public void sendWithdrawalCode(String email, String code) {
        mailSender.send(email, "[Pokade] 회원 탈퇴 인증 코드",
                VerificationMailTemplate.codeMail(
                        "회원 탈퇴 인증 코드",
                        "아래 코드를 입력하시면 탈퇴 절차가 진행됩니다. 탈퇴 후에는 되돌릴 수 없습니다.",
                        code, CODE_TTL_MINUTES));
    }
}
