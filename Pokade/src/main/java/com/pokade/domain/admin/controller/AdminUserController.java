package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.dto.response.AdminUserResponse;
import com.pokade.domain.admin.service.AdminUserService;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<Page<AdminUserResponse>> getUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = {"created_At", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(adminUserService.getUsers(status, role, keyword, pageable));
    }

    @PatchMapping("/{userId}/suspend")
    public ApiResponse<Void> suspend(@AuthenticationPrincipal Long adminId,
                                     @PathVariable Long userId) {
        adminUserService.suspend(adminId, userId);
        return ApiResponse.ok("계정을 정지했습니다.");
    }

    @PatchMapping("/{userId}/unsuspend")
    public ApiResponse<Void> unsuspend(@AuthenticationPrincipal Long adminId,
                                       @PathVariable Long userId) {
        adminUserService.unsuspend(adminId, userId);
        return ApiResponse.ok("정지를 해제했습니다.");
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> forceWithdraw(@AuthenticationPrincipal Long adminId,
                                           @PathVariable Long userId) {
        adminUserService.forceWithdraw(adminId, userId);
        return ApiResponse.ok("계정을 탈퇴 처리했습니다.");
    }
}
