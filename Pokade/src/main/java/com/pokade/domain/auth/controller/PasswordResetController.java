package com.pokade.domain.auth.controller;

import com.pokade.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.pokade.domain.auth.dto.request.PasswordResetSendRequest;
import com.pokade.domain.auth.service.PasswordResetService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password/reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/send")
    public ApiResponse<Void> sendResetCode(@Valid @RequestBody PasswordResetSendRequest request) {
        passwordResetService.send(request.email());
        return ApiResponse.ok("비밀번호 재설정 코드가 발송되었습니다.");
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.email(), request.code(), request.newPassword());
        return ApiResponse.ok("비밀번호가 재설정되었습니다.");
    }

}
