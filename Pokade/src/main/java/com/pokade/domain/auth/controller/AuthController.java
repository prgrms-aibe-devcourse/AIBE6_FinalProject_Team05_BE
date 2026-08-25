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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입·로그인·토큰 재발급·로그아웃·소셜 회원가입 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2LoginService oauth2LoginService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Operation(
            summary = "회원가입",
            description = "이메일·비밀번호로 계정을 생성하고 약관 동의 이력을 함께 기록합니다. 가입 직후 계정은 "
                    + "PENDING 상태이며, 이메일 인증을 완료해야 로그인할 수 있습니다. 이미 가입된 이메일이거나 "
                    + "중복된 닉네임이면 실패합니다."
    )
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ApiResponse.ok(
                "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.",
                authService.signup(request)
        );
    }

    @Operation(
            summary = "로그인",
            description = "이메일·비밀번호로 로그인하고 access token은 응답 본문으로, refresh token은 HttpOnly 쿠키로 "
                    + "내려줍니다. 이메일 미인증(PENDING)·정지(SUSPENDED) 계정은 실패하며, 로그인 실패가 "
                    + "일정 횟수를 넘으면 일시적으로 차단됩니다. 탈퇴 신청 상태(WITHDRAWAL_PENDING)에서는 "
                    + "철회를 위해 로그인이 허용됩니다."
    )
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

    @Operation(
            summary = "액세스 토큰 재발급",
            description = "refreshToken 쿠키로 새 access token을 발급합니다. refresh token도 함께 회전하며, "
                    + "동시 요청이 grace 구간으로 수렴한 경우에는 새 refresh 쿠키를 내리지 않고 기존 쿠키를 유지합니다. "
                    + "재사용이 탐지되면 해당 세션이 폐기됩니다."
    )
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

    @Operation(
            summary = "로그아웃",
            description = "서버에 저장된 refresh token을 삭제하고 쿠키를 만료시킵니다. 쿠키가 없거나 이미 "
                    + "만료된 토큰이어도 성공으로 응답하는 멱등 API입니다."
    )
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

    @Operation(
            summary = "소셜 회원가입 완료",
            description = "소셜 로그인 과정에서 발급된 가입 티켓과 추가 입력값(닉네임·약관 동의)으로 계정 생성을 "
                    + "마무리하고 곧바로 로그인 상태로 만듭니다. 이미 가입된 소셜 계정이거나 티켓이 만료됐으면 실패합니다."
    )
    @PostMapping("/oauth2/register")
    public ApiResponse<LoginResponse> oauth2Register(
            @Valid @RequestBody OAuth2RegisterRequest request,
            HttpServletResponse response
    ) {
        TokenPair tokens = oauth2LoginService.register(request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshTokenCookieFactory.create(tokens.refreshToken()).toString());
        return ApiResponse.ok("소셜 회원가입이 완료되었습니다.", LoginResponse.of(tokens.accessToken()));
    }

}
