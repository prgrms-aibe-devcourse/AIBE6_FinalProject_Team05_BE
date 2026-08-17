package com.pokade.domain.user.controller;

import com.pokade.domain.user.dto.request.NicknameUpdateRequest;
import com.pokade.domain.user.dto.request.PasswordUpdateRequest;
import com.pokade.domain.user.dto.request.WithdrawalRequest;
import com.pokade.domain.user.dto.response.MyProfileResponse;
import com.pokade.domain.user.dto.response.PublicProfileResponse;
import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.service.ProfileService;
import com.pokade.domain.user.service.UserService;
import com.pokade.domain.user.service.WithdrawalService;
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
    private final WithdrawalService withdrawalService;
    private final ProfileService profileService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok("내 정보 조회 성공", userService.getMyInfo(userId));
    }

    @GetMapping("/me/profile")
    public ApiResponse<MyProfileResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok("내 프로필 조회 성공", profileService.getMyProfile(userId));
    }

    @GetMapping("/{userId}")
    public ApiResponse<PublicProfileResponse> getPublicProfile(@PathVariable Long userId) {
        return ApiResponse.ok("공개 프로필 조회 성공", profileService.getPublicProfile(userId));
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

    @DeleteMapping("/me")
    public ApiResponse<Void> requestWithdrawal(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody WithdrawalRequest request) {
        withdrawalService.requestWithdrawal(userId, request.password(), request.code());
        return ApiResponse.ok("탈퇴 신청이 접수되었습니다.");
    }

    @PostMapping("/me/withdrawal/cancel")
    public ApiResponse<Void> cancelWithdrawal(@AuthenticationPrincipal Long userId) {
        withdrawalService.cancelWithdrawal(userId);
        return ApiResponse.ok("탈퇴 신청이 철회되었습니다.");
    }

    @PostMapping("/me/withdrawal/send-code")
    public ApiResponse<Void> sendWithdrawalCode(@AuthenticationPrincipal Long userId) {
        withdrawalService.sendWithdrawalCode(userId);
        return ApiResponse.ok("탈퇴 인증 코드가 발송되었습니다.");
    }
}
