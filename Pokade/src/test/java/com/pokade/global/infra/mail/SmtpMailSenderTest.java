package com.pokade.global.infra.mail;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
public class SmtpMailSenderTest {

    @Mock
    JavaMailSender javaMailSender;
    @InjectMocks
    SmtpMailSender smtpMailSender;

    @Test
    @DisplayName("수신자·제목·본문을 담은 메일을 전송한다")
    void send_deliversMessage() {
        smtpMailSender.send("user@pokade.com", "제목", "본문");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        then(javaMailSender).should().send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@pokade.com");
        assertThat(sent.getSubject()).isEqualTo("제목");
        assertThat(sent.getText()).isEqualTo("본문");
    }

    @Test
    @DisplayName("메일 서버 전송 실패 시 EMAIL_SEND_FAILED 예외를 던진다")
    void send_throwsWhenMailFails() {
        willThrow(new MailSendException("smtp down"))
                .given(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> smtpMailSender.send("user@pokade.com", "제목", "본문"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

}
