package com.pokade.domain.auth.support;

import com.pokade.global.infra.mail.MailSender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationMailSender {

    // 메일 문구에 노출하는 코드 유효시간 - 실제 만료는 각  CodeStore의 CODE_TTL에 의해 결정된다.
    private static final int CODE_TTL_MINUTES = 5;

    /**
     * 발송 결과 카운터 - 발송이 @Async라 API는 이미 200을 반환한 뒤다
     * "발송 요청 접수 수"일 뿐이므로, 실제 전송을 감싸는 이 지점에서 성공, 실패를 센다.
     */
    private static final String SEND_METRIC = "auth.mail.send";

    private final MailSender mailSender;
    private final MeterRegistry meterRegistry;

    // 인증 코드 메일을 비동기로 발송한다.
    @Async
    public void sendCode(String email, String code) {
        send("verification", email, "[Pokade] 이메일 인증 코드",
                VerificationMailTemplate.codeMail(
                        "이메일 인증 코드",
                        "아래 코드를 입력하시면 이메일 인증이 완료됩니다.",
                        code, CODE_TTL_MINUTES));
    }

    // 비밀번호 재설정 코드 메일을 비동기로 발송한다.
    @Async
    public void sendResetCode(String email, String code) {
        send("password_reset", email, "[Pokade] 비밀번호 재설정 코드",
                VerificationMailTemplate.codeMail(
                        "비밀번호 재설정 코드",
                        "아래 코드를 입력하시면 새 비밀번호를 설정할 수 있습니다.",
                        code, CODE_TTL_MINUTES));
    }

    // 탈퇴 인증 코드 메일을 보낸다
    @Async
    public void sendWithdrawalCode(String email, String code) {
        send("withdrawal", email, "[Pokade] 회원 탈퇴 인증 코드",
                VerificationMailTemplate.codeMail(
                        "회원 탈퇴 인증 코드",
                        "아래 코드를 입력하시면 탈퇴 절차가 진행됩니다. 탈퇴 후에는 되돌릴 수 없습니다.",
                        code, CODE_TTL_MINUTES));
    }

    // 메일을 보내고 결과를 지표에 기록한다. 예외는 다시 던져 기존 비동기 실패 로깅을 유지한다.
    private void send(String type, String email, String subject, String body) {
        try {
            mailSender.send(email, subject, body);
            count(type, "success");
        } catch (RuntimeException e) {
            count(type, "failure");
            throw e;
        }
    }

    // auth.mail.send 카운터를 종류,결과 태그와 함께 증가시킨다.
    private void count(String type, String result) {
        Counter.builder(SEND_METRIC)
                .tag("type", type)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
