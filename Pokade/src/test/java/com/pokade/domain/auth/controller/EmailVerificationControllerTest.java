package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.service.EmailVerificationService;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@WebMvcTest(EmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailVerificationControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("유효한 이메일이면 200과 함께 인증 코드 발송 서비스를 호출한다.")
    void send_ok() {
        mockMvcTester.post()
                .uri("/api/auth/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@pokade.com\"}")
                .assertThat()
                .hasStatusOk();

        then(emailVerificationService).should().send("user@pokade.com");
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void send_invalidEmail() {
        mockMvcTester.post()
                .uri("/api/auth/email/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST);

        then(emailVerificationService).should(never()).send(any());
    }

    @Test
    @DisplayName("유효한 이메일과 6자리 코드면 200과 함께 인증 서비스를 호출한다.")
    void verify_ok() {
        mockMvcTester.post()
                .uri("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@pokade.com\",\"code\":\"123456\"}")
                .assertThat()
                .hasStatusOk();

        then(emailVerificationService).should().verify("user@pokade.com", "123456");
    }

    @Test
    @DisplayName("코드 형식이 6자리 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다.")
    void verify_invalidCode() {
        mockMvcTester.post()
                .uri("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@pokade.com\",\"code\":\"12ab\"}")
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST);

        then(emailVerificationService).should(never()).verify(any(), any());
    }
}
