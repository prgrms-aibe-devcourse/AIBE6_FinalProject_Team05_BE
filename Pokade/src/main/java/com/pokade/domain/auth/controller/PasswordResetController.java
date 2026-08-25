package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.pokade.domain.auth.dto.request.PasswordResetSendRequest;
import com.pokade.domain.auth.service.PasswordResetService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "비밀번호 재설정", description = "비밀번호 찾기 - 재설정 코드 발송 및 새 비밀번호 설정 API")
@RestController
@RequestMapping("/api/auth/password/reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "재설정 코드 발송",
            description = "가입된 이메일로 비밀번호 재설정 코드를 발송합니다. 가입되지 않은 이메일, 비밀번호가 "
                    + "없는 소셜 전용 계정, 이메일 인증을 마치지 않은 계정은 실패합니다. 재발송에는 쿨다운이 적용됩니다."
    )
    @PostMapping("/send")
    public ApiResponse<Void> sendResetCode(@Valid @RequestBody PasswordResetSendRequest request) {
        passwordResetService.send(request.email());
        return ApiResponse.ok("비밀번호 재설정 코드가 발송되었습니다.");
    }

    @Operation(
            summary = "비밀번호 재설정",
            description = "재설정 코드를 검증한 뒤 새 비밀번호로 변경합니다. 코드 검증에 성공해야만 비밀번호가 "
                    + "바뀌며, 코드가 틀리거나 만료됐거나 시도 횟수를 초과하면 기존 비밀번호가 그대로 유지됩니다."
    )
    @PostMapping("/confirm")
    public ApiResponse<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.email(), request.code(), request.newPassword());
        return ApiResponse.ok("비밀번호가 재설정되었습니다.");
    }

}
