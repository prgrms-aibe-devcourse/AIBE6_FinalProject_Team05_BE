package com.pokade.global.infra.mail;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
public class SmtpMailSenderTest {

    @Mock
    JavaMailSender javaMailSender;
    @InjectMocks
    SmtpMailSender smtpMailSender;

    // mock은 createMimeMessage()에 null을 돌려주므로, 실제로 채워 넣을 수 있는 빈 MimeMessage를 만들어 준다.
    private MimeMessage emptyMimeMessage() {
        return new JavaMailSenderImpl().createMimeMessage();
    }

    @Test
    @DisplayName("수신자·제목·HTML 본문을 담은 메일을 전송한다")
    void send_deliversMessage() throws Exception {
        given(javaMailSender.createMimeMessage()).willReturn(emptyMimeMessage());

        smtpMailSender.send("user@pokade.com", "제목", "<p>본문</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        then(javaMailSender).should().send(captor.capture());

        MimeMessage sent = captor.getValue();
        // Content-Type 헤더는 saveChanges() 시점에 기록된다 — 실제 발송에서는 JavaMailSenderImpl이
        // 호출하지만 여기서는 send를 mock으로 잡아 그 단계를 거치지 않으므로 직접 확정시킨다.
        sent.saveChanges();

        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@pokade.com");
        assertThat(sent.getSubject()).isEqualTo("제목");
        assertThat(sent.getContent().toString()).contains("<p>본문</p>");
        assertThat(sent.getContentType()).contains("text/html");
        assertThat(sent.getContentType()).contains("UTF-8");
    }

    @Test
    @DisplayName("메일 서버 전송 실패 시 EMAIL_SEND_FAILED 예외를 던진다")
    void send_throwsWhenMailFails() {
        given(javaMailSender.createMimeMessage()).willReturn(emptyMimeMessage());
        willThrow(new MailSendException("smtp down"))
                .given(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> smtpMailSender.send("user@pokade.com", "제목", "<p>본문</p>"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

}
