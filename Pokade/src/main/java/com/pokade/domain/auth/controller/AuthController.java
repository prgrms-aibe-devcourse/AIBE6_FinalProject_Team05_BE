package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.auth.service.AuthService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ApiResponse.ok(
                "회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.",
                authService.signup(request)
        );
    }
}
