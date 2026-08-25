package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.request.EmailSendRequest;
import com.pokade.domain.auth.dto.request.EmailVerifyRequest;
import com.pokade.domain.auth.service.EmailVerificationService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이메일 인증", description = "회원가입 이메일 인증 코드 발송/확인 API")
@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @Operation(
            summary = "인증 코드 발송",
            description = "회원가입한 이메일로 인증 코드를 발송합니다. 가입되지 않은 이메일이거나 이미 인증을 "
                    + "마친 계정이면 실패합니다. 재발송에는 쿨다운이 적용되어, 쿨다운 중에 다시 호출하면 실패합니다."
    )
    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody EmailSendRequest request) {
        emailVerificationService.send(request.email());
        return ApiResponse.ok("이메일 인증 코드가 발송되었습니다.");
    }

    @Operation(
            summary = "인증 코드 확인",
            description = "발송된 코드를 검증하고 계정을 인증 완료(ACTIVE) 상태로 전환합니다. 코드가 틀리거나 "
                    + "만료됐으면 실패하며, 시도 횟수를 초과하면 코드가 폐기되어 재발송이 필요합니다."
    )
    @PostMapping("/verify")
    public ApiResponse<Void> verify(@Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return ApiResponse.ok("이메일 인증이 완료되었습니다.");
    }
}
