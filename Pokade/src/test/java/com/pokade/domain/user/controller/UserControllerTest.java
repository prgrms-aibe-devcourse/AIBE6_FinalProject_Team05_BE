package com.pokade.domain.user.controller;

import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.service.UserService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;                 // 실제 JwtAuthenticationFilter가 요구
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // SecurityConfig가 요구

    private RequestPostProcessor userId(Long userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    @Test
    @DisplayName("인증된 사용자가 내 정보를 조회하면 200과 프로필을 반환한다")
    void getMyInfo_success() throws Exception {
        UserResponse res = new UserResponse(
                1L, "user@pokade.com", "트레이너김",
                Role.USER, UserStatus.ACTIVE, "https://img/x.png", 30);
        given(userService.getMyInfo(1L)).willReturn(res);

        mockMvc.perform(get("/api/users/me").with(userId(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.email").value("user@pokade.com"))
                .andExpect(jsonPath("$.data.nickname").value("트레이너김"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.pointBalance").value(30));
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 404와 USER_NOT_FOUND를 반환한다")
    void getMyInfo_userNotFound() throws Exception {
        given(userService.getMyInfo(1L))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/me").with(userId(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}