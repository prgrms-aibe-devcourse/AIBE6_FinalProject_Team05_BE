package com.pokade.domain.user.controller;

import com.pokade.domain.user.dto.request.NicknameUpdateRequest;
import com.pokade.domain.user.dto.request.PasswordUpdateRequest;
import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.service.UserService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok("내 정보 조회 성공", userService.getMyInfo(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<Void> updateNickname(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody NicknameUpdateRequest request) {
        userService.updateNickname(userId, request.nickname());
        return ApiResponse.ok("닉네임 변경 성공");
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody PasswordUpdateRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ApiResponse.ok("비밀번호가 변경되었습니다.");
    }
}
