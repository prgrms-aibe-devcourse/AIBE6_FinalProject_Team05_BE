package com.pokade.domain.user.controller;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.service.ProfileImageService;
import com.pokade.domain.user.support.ProfileImagePath;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import com.pokade.global.security.oauth.CustomOAuth2UserService;
import com.pokade.global.security.oauth.OAuth2LoginFailureHandler;
import com.pokade.global.security.oauth.OAuth2LoginSuccessHandler;
import com.pokade.global.security.oauth.RedisAuthorizationRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileImageController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class}) // 인가 계약을 검증하려면 실물 시큐리티 설정이 필요
class ProfileImageControllerTest {

    private static final Long USER_ID = 3L;
    private static final String ETAG = "\"2ad5c2971e6b21e1247833b781d9ad55\"";
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileImageService profileImageService;                  // ProfileImageController가 요구
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;                        // 실제 JwtAuthenticationFilter가 요구
    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;                  // JwtAuthenticationFilter가 요구
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;      // SecurityConfig가 요구
    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;      // SecurityConfig가 요구
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;          // SecurityConfig가 요구
    @MockitoBean
    private RedisAuthorizationRequestRepository authorizationRequestRepository; // SecurityConfig가 요구

    // 응답 DTO가 실제로 내려보내는 경로를 그대로 요청한다 - 컨트롤러 매핑이 바뀌면 여기서 404로 드러난다.
    private String servePath() {
        User user = User.builder().id(USER_ID).profileImageUrl("profile/9f3c2a.png").build();
        return ProfileImagePath.of(user);
    }

    private ResponseEntity<org.springframework.core.io.Resource> imageResponse() {
        return ResponseEntity.ok()
                .eTag(ETAG)
                .contentType(MediaType.IMAGE_PNG)
                .body(new ByteArrayResource(PNG_BYTES));
    }

    @Test
    @DisplayName("응답에 실려 나가는 경로로 요청하면 토큰 없이도 이미지가 내려온다")
    void serve_withoutToken_ok() throws Exception {
        given(profileImageService.serve(eq(USER_ID), isNull())).willReturn(imageResponse());

        mockMvc.perform(get(servePath()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG_BYTES))
                .andExpect(header().string(HttpHeaders.ETAG, ETAG));

        then(profileImageService).should().serve(eq(USER_ID), isNull());
    }

    @Test
    @DisplayName("캐시 무효화용 쿼리스트링이 붙어도 토큰 없이 조회된다")
    void serve_withCacheBuster_ok() throws Exception {
        given(profileImageService.serve(eq(USER_ID), isNull())).willReturn(imageResponse());

        mockMvc.perform(get(servePath() + "?v=1755500000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("If-None-Match 헤더가 서비스로 전달되고 변경이 없으면 304를 반환한다")
    void serve_notModified() throws Exception {
        given(profileImageService.serve(USER_ID, ETAG))
                .willReturn(ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(ETAG).build());

        mockMvc.perform(get(servePath()).header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));

        then(profileImageService).should().serve(USER_ID, ETAG);
    }
}
