package com.pokade.domain.user.controller;

import com.pokade.domain.user.dto.request.MarketingAgreementRequest;
import com.pokade.domain.user.dto.request.NicknameUpdateRequest;
import com.pokade.domain.user.dto.request.PasswordUpdateRequest;
import com.pokade.domain.user.dto.request.WithdrawalRequest;
import com.pokade.domain.user.dto.response.MyProfileResponse;
import com.pokade.domain.user.dto.response.PublicProfileResponse;
import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.service.*;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "회원", description = "내 정보·프로필·비밀번호·마케팅 동의·회원탈퇴 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WithdrawalService withdrawalService;
    private final ProfileService profileService;
    private final ProfileImageService profileImageService;
    private final UserAgreementService userAgreementService;

    @Operation(summary = "내 정보 조회", description = "로그인한 회원의 계정 기본 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok("내 정보 조회 성공", userService.getMyInfo(userId));
    }

    @Operation(
            summary = "내 프로필 조회",
            description = "마이페이지용 상세 프로필을 조회합니다. 공개 프로필보다 많은 정보를 포함합니다."
    )
    @GetMapping("/me/profile")
    public ApiResponse<MyProfileResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok("내 프로필 조회 성공", profileService.getMyProfile(userId));
    }

    @Operation(
            summary = "공개 프로필 조회",
            description = "다른 회원의 공개 프로필을 조회합니다. 비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/{userId}")
    public ApiResponse<PublicProfileResponse> getPublicProfile(
            @Parameter(description = "회원 ID") @PathVariable Long userId) {
        return ApiResponse.ok("공개 프로필 조회 성공", profileService.getPublicProfile(userId));
    }

    @Operation(
            summary = "닉네임 변경",
            description = "닉네임을 변경합니다. 이미 사용 중인 닉네임이면 실패하고, 변경 쿨다운이 지나지 않았으면 "
                    + "실패합니다. 현재 닉네임과 같은 값을 보내면 아무것도 바꾸지 않고 성공으로 응답합니다."
    )
    @PatchMapping("/me")
    public ApiResponse<Void> updateNickname(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody NicknameUpdateRequest request) {
        userService.updateNickname(userId, request.nickname());
        return ApiResponse.ok("닉네임 변경 성공");
    }

    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. 현재 비밀번호가 틀리면 실패하고, "
                    + "비밀번호가 없는 소셜 전용 계정은 사용할 수 없습니다. 변경에 성공하면 모든 기기의 "
                    + "리프레시 토큰이 무효화되어 다시 로그인해야 합니다."
    )
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody PasswordUpdateRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ApiResponse.ok("비밀번호가 변경되었습니다.");
    }

    @Operation(
            summary = "마케팅 수신 동의 변경",
            description = "마케팅 정보 수신 동의를 켜거나 끕니다. 변경할 때마다 동의 이력이 새로 기록됩니다."
    )
    @PatchMapping("/me/agreements/marketing")
    public ApiResponse<Void> changeMarketingAgreement(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody MarketingAgreementRequest request) {
        userAgreementService.changeMarketing(userId, request.agreed());
        return ApiResponse.ok(request.agreed() ? "마케팅 수신에 동의했습니다." : "마케팅 수신 동의를 해제했습니다.");
    }

    @Operation(
            summary = "회원탈퇴 신청",
            description = "본인 확인 후 탈퇴를 신청합니다. 일반 계정은 비밀번호로, 소셜 전용 계정은 별도로 발송받은 "
                    + "이메일 인증 코드로 본인을 확인합니다. 즉시 삭제가 아니라 유예 기간을 두는 탈퇴 신청 상태로 "
                    + "전환되며, 유예 기간 동안은 철회할 수 있습니다. 정상(ACTIVE) 상태가 아니면 실패합니다."
    )
    @DeleteMapping("/me")
    public ApiResponse<Void> requestWithdrawal(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody WithdrawalRequest request) {
        withdrawalService.requestWithdrawal(userId, request.password(), request.code());
        return ApiResponse.ok("탈퇴 신청이 접수되었습니다.");
    }

    @Operation(
            summary = "회원탈퇴 철회",
            description = "유예 기간 중인 탈퇴 신청을 철회하고 계정을 정상 상태로 되돌립니다. "
                    + "탈퇴 신청 상태가 아니면 실패합니다."
    )
    @PostMapping("/me/withdrawal/cancel")
    public ApiResponse<Void> cancelWithdrawal(@AuthenticationPrincipal Long userId) {
        withdrawalService.cancelWithdrawal(userId);
        return ApiResponse.ok("탈퇴 신청이 철회되었습니다.");
    }

    @Operation(
            summary = "탈퇴 인증 코드 발송",
            description = "소셜 전용 계정의 탈퇴 본인 확인을 위해 가입 이메일로 인증 코드를 발송합니다. "
                    + "발송된 코드는 회원탈퇴 신청 요청에 담아 보냅니다. 비밀번호가 있는 일반 계정은 "
                    + "비밀번호로 본인 확인을 하므로 사용할 수 없습니다."
    )
    @PostMapping("/me/withdrawal/send-code")
    public ApiResponse<Void> sendWithdrawalCode(@AuthenticationPrincipal Long userId) {
        withdrawalService.sendWithdrawalCode(userId);
        return ApiResponse.ok("탈퇴 인증 코드가 발송되었습니다.");
    }

    @Operation(
            summary = "프로필 이미지 등록",
            description = "프로필 이미지를 업로드합니다(multipart/form-data). 기존 이미지가 있으면 교체됩니다. "
                    + "JPEG·PNG만 허용하며 5MB를 넘으면 실패합니다."
    )
    @PostMapping("/me/profile/image")
    public ApiResponse<Void> uploadProfileImage(@AuthenticationPrincipal Long userId,
                                                @RequestPart MultipartFile image) {
        profileImageService.upload(userId, image);
        return ApiResponse.ok("프로필 이미지가 등록되었습니다.");
    }

    @Operation(
            summary = "프로필 이미지 삭제",
            description = "등록된 프로필 이미지를 삭제하고 기본 이미지로 되돌립니다. "
                    + "등록된 이미지가 없으면 실패합니다."
    )
    @DeleteMapping("/me/profile/image")
    public ApiResponse<Void> deleteProfileImage(@AuthenticationPrincipal Long userId) {
        profileImageService.delete(userId);
        return ApiResponse.ok("프로필 이미지가 삭제되었습니다.");
    }
}
