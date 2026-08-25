package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.dto.response.AdminUserResponse;
import com.pokade.domain.admin.service.AdminUserService;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 - 회원", description = "회원 목록 조회 및 정지/정지해제/강제탈퇴 처리 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminUserService adminUserService;

    @Operation(
            summary = "회원 목록 조회",
            description = "상태·권한·키워드로 회원을 검색해 최신 가입순으로 페이징 조회합니다. "
                    + "조건을 생략하면 해당 항목 전체를 대상으로 합니다."
    )
    @GetMapping
    public ApiResponse<Page<AdminUserResponse>> getUsers(
            @Parameter(description = "회원 상태") @RequestParam(required = false) UserStatus status,
            @Parameter(description = "권한") @RequestParam(required = false) Role role,
            @Parameter(description = "이메일·닉네임 검색어") @RequestParam(required = false) String keyword,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = {"created_At", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(adminUserService.getUsers(status, role, keyword, pageable));
    }

    @Operation(
            summary = "계정 정지",
            description = "회원 계정을 정지 상태로 전환하고 해당 회원의 모든 리프레시 토큰을 삭제해 강제 로그아웃시킵니다. "
                    + "정상(ACTIVE) 상태인 회원만 정지할 수 있으며, 관리자 본인이나 다른 관리자 계정은 대상으로 삼을 수 없습니다."
    )
    @PatchMapping("/{userId}/suspend")
    public ApiResponse<Void> suspend(@AuthenticationPrincipal Long adminId,
                                     @Parameter(description = "대상 회원 ID") @PathVariable Long userId) {
        adminUserService.suspend(adminId, userId);
        return ApiResponse.ok("계정을 정지했습니다.");
    }

    @Operation(
            summary = "계정 정지 해제",
            description = "정지된 회원 계정을 정상 상태로 되돌립니다. 정지 상태가 아닌 회원이면 실패합니다."
    )
    @PatchMapping("/{userId}/unsuspend")
    public ApiResponse<Void> unsuspend(@AuthenticationPrincipal Long adminId,
                                       @Parameter(description = "대상 회원 ID") @PathVariable Long userId) {
        adminUserService.unsuspend(adminId, userId);
        return ApiResponse.ok("정지를 해제했습니다.");
    }

    @Operation(
            summary = "계정 강제 탈퇴",
            description = "관리자가 회원을 탈퇴 처리합니다. 회원 본인의 탈퇴 신청과 달리 유예 기간 없이 곧바로 "
                    + "탈퇴가 확정됩니다. 이미 탈퇴한 회원이거나, 관리자 본인 또는 다른 관리자 계정이면 실패합니다."
    )
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> forceWithdraw(@AuthenticationPrincipal Long adminId,
                                           @Parameter(description = "대상 회원 ID") @PathVariable Long userId) {
        adminUserService.forceWithdraw(adminId, userId);
        return ApiResponse.ok("계정을 탈퇴 처리했습니다.");
    }
}
