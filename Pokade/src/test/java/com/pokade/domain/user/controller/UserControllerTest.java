package com.pokade.domain.user.controller;

import com.pokade.domain.user.dto.response.MyProfileResponse;
import com.pokade.domain.user.dto.response.PublicProfileResponse;
import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.service.ProfileImageService;
import com.pokade.domain.user.service.ProfileService;
import com.pokade.domain.user.service.UserAgreementService;
import com.pokade.domain.user.service.UserService;
import com.pokade.domain.user.service.WithdrawalService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class}) // 401 계약을 검증하려면 실물 엔트리포인트가 필요
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;                 // 실제 JwtAuthenticationFilter가 요구
    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;                 // JwtAuthenticationFilter가 요구
    @MockitoBean
    private WithdrawalService withdrawalService;                     // UserController가 요구
    @MockitoBean
    private ProfileService profileService;                           // UserController가 요구
    @MockitoBean
    private ProfileImageService profileImageService;                 // UserController가 요구
    @MockitoBean
    private UserAgreementService userAgreementService;                // UserController가 요구
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;      // SecurityConfig가 요구
    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;      // SecurityConfig가 요구
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;          // SecurityConfig가 요구
    @MockitoBean
    private RedisAuthorizationRequestRepository authorizationRequestRepository; // SecurityConfig가 요구

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
                Role.USER, UserStatus.ACTIVE, "https://img/x.png", 30, Provider.LOCAL, null);
        given(userService.getMyInfo(1L)).willReturn(res);

        mockMvc.perform(get("/api/users/me").with(userId(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.email").value("user@pokade.com"))
                .andExpect(jsonPath("$.data.nickname").value("트레이너김"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.pointBalance").value(30))
                .andExpect(jsonPath("$.data.provider").value("LOCAL"))
                .andExpect(jsonPath("$.data.withdrawalRequestedAt").value(nullValue()));
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

    @Test
    @DisplayName("인증된 사용자가 내 프로필 상세를 조회하면 200과 상세 필드를 반환한다")
    void getMyProfile_success() throws Exception {
        MyProfileResponse res = new MyProfileResponse(
                "user@pokade.com", Provider.GOOGLE, true,
                LocalDateTime.of(2026, 1, 2, 3, 4), true);
        given(profileService.getMyProfile(1L)).willReturn(res);

        mockMvc.perform(get("/api/users/me/profile").with(userId(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@pokade.com"))
                .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.data.socialLinked").value(true))
                .andExpect(jsonPath("$.data.marketingAgreed").value(true));

        then(profileService).should().getMyProfile(1L);
    }

    // ===== 인가 회귀: 공개 프로필을 열면서 /me 까지 열리지 않았는지 =====

    @Test
    @DisplayName("인가: 토큰 없이 GET /api/users/me 는 401이고 컨트롤러까지 도달하지 못한다")
    void getMyInfo_withoutToken_blocked() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        then(userService).should(never()).getMyInfo(any());
    }

    @Test
    @DisplayName("인가: 공개 프로필은 토큰 없이도 조회된다")
    void getPublicProfile_withoutToken_ok() throws Exception {
        given(profileService.getPublicProfile(1L)).willReturn(
                new PublicProfileResponse(1L, "지우", "https://img/x.png", null, 3L, 2L));

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("지우"))
                .andExpect(jsonPath("$.data.completedTradeCount").value(3))
                .andExpect(jsonPath("$.data.activeListingCount").value(2));
    }

    @Test
    @DisplayName("인가: 공개 프로필에 쿼리스트링이 붙어도 인증 없이 조회된다")
    void getPublicProfile_withQueryString_ok() throws Exception {
        given(profileService.getPublicProfile(1L)).willReturn(
                new PublicProfileResponse(1L, "지우", null, null, 3L, 2L));

        mockMvc.perform(get("/api/users/1?ref=search"))
                .andExpect(status().isOk());
    }

    // ===== 인가 회귀: 프로필 이미지 =====

    @Test
    @DisplayName("인가: 토큰 없이 프로필 이미지를 업로드하면 401이고 서비스까지 도달하지 못한다")
    void uploadProfileImage_withoutToken_blocked() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/users/me/profile/image").file(image))
                .andExpect(status().isUnauthorized());

        then(profileImageService).should(never()).upload(any(), any());
    }

    @Test
    @DisplayName("인가: 토큰 없이 프로필 이미지를 삭제하면 401이고 서비스까지 도달하지 못한다")
    void deleteProfileImage_withoutToken_blocked() throws Exception {
        mockMvc.perform(delete("/api/users/me/profile/image"))
                .andExpect(status().isUnauthorized());

        then(profileImageService).should(never()).delete(any());
    }

    @Test
    @DisplayName("인가: 인증된 사용자는 프로필 이미지를 업로드할 수 있다")
    void uploadProfileImage_success() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/users/me/profile/image").file(image).with(userId(1L)))
                .andExpect(status().isOk());

        then(profileImageService).should().upload(eq(1L), any());
    }
}