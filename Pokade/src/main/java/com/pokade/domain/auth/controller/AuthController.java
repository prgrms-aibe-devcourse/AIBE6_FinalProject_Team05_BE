package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.LoginResponse;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.auth.service.AuthService;
import com.pokade.global.response.ApiResponse;
import com.pokade.global.security.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    private final AuthService authService;
    private final JwtProperties jwtProperties;

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

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokens.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(jwtProperties.refreshExpiration()) // 토큰 만료와 동기화
                .sameSite("Lax") //CSRF 관련 로컬(3000)/API(8080)가 같은 site(localhost)로 인식되도록 lax로 설정
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ApiResponse.ok("로그인 성공", LoginResponse.of(tokens.accessToken()));
    }
}
