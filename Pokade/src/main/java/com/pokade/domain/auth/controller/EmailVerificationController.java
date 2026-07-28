package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.request.EmailSendRequest;
import com.pokade.domain.auth.dto.request.EmailVerifyRequest;
import com.pokade.domain.auth.service.EmailVerificationService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody EmailSendRequest request) {
        emailVerificationService.send(request.email());
        return ApiResponse.ok("이메일 인증 코드가 발송되었습니다.");
    }

    @PostMapping("/verify")
    public ApiResponse<Void> verify(@Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return ApiResponse.ok("이메일 인증이 완료되었습니다.");
    }
}
