package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.OAuth2RegisterRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.LoginResponse;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.auth.service.AuthService;
import com.pokade.domain.auth.service.OAuth2LoginService;
import com.pokade.global.response.ApiResponse;
import com.pokade.global.web.RefreshTokenCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2LoginService oauth2LoginService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ApiResponse.ok(
                "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.",
                authService.signup(request)
        );
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        TokenPair tokens = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookieFactory.create(tokens.refreshToken()).toString());
        return ApiResponse.ok("로그인 성공", LoginResponse.of(tokens.accessToken()));
    }

    @PostMapping("/reissue")
    public ApiResponse<LoginResponse> reissue(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        TokenPair tokens = authService.reissue(refreshToken);
        if (tokens.refreshToken() != null) { // grace 수렴이면 refresh 없음 -> 기존 쿠키 유지, 재세팅 스킵
            response.addHeader(HttpHeaders.SET_COOKIE,
                    refreshTokenCookieFactory.create(tokens.refreshToken()).toString());
        }
        return ApiResponse.ok("토큰 재발급 성공", LoginResponse.of(tokens.accessToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookieFactory.expired().toString());
        return ApiResponse.ok("로그아웃 성공");
    }

    @PostMapping("/oauth2/register")
    public ApiResponse<LoginResponse> oauth2Register(
            @Valid @RequestBody OAuth2RegisterRequest request,
            HttpServletResponse response
    ) {
        TokenPair tokens = oauth2LoginService.register(request.ticket(), request.nickname());
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookieFactory.create(tokens.refreshToken()).toString());
        return ApiResponse.ok("소셜 회원가입이 완료되었습니다.", LoginResponse.of(tokens.accessToken()));
    }

}
