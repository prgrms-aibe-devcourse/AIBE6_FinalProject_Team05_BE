package com.pokade.domain.ai.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.pokade.domain.ai.service.AiGradeService;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;

/**
 * 인증 필터(addFilters=false)를 꺼둔 상태라 @AuthenticationPrincipal은 항상 null로 주입된다.
 * 즉 이 테스트는 "principal이 없으면 컨트롤러가 401을 던지는지"만 검증하고,
 * JWT 필터 자체의 동작(토큰 파싱 등)은 검증 대상이 아니다.
 */
@WebMvcTest(AiGradeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiGradeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private AiGradeService aiGradeService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @Test
    @DisplayName("인증 없이 진단 결과를 조회하면 401을 반환한다")
    void getGradeResult_withoutAuth_returnsUnauthorized() {
        mockMvcTester.get()
                .uri("/api/ai/grade/1")
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증 없이 진단 이력을 조회하면 401을 반환한다")
    void getGradeHistory_withoutAuth_returnsUnauthorized() {
        mockMvcTester.get()
                .uri("/api/ai/grade/history")
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증 없이 진단을 요청하면 401을 반환한다")
    void grade_withoutAuth_returnsUnauthorized() {
        MockMultipartFile front = new MockMultipartFile("front", "front.png", "image/png", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.png", "image/png", new byte[]{1});
        MockMultipartFile cornerTl = new MockMultipartFile("corner_tl", "tl.png", "image/png", new byte[]{1});
        MockMultipartFile cornerTr = new MockMultipartFile("corner_tr", "tr.png", "image/png", new byte[]{1});
        MockMultipartFile cornerBl = new MockMultipartFile("corner_bl", "bl.png", "image/png", new byte[]{1});
        MockMultipartFile cornerBr = new MockMultipartFile("corner_br", "br.png", "image/png", new byte[]{1});

        mockMvcTester.post()
                .uri("/api/ai/grade")
                .multipart()
                .file(front).file(back).file(cornerTl).file(cornerTr).file(cornerBl).file(cornerBr)
                .assertThat()
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
