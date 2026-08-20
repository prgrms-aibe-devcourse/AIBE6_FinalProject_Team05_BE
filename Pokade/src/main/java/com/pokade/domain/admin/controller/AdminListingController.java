package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.service.AdminListingService;
import com.pokade.domain.report.dto.ReportResponse;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "관리자 - 매물", description = "매물 신고 조회 및 숨김 처리 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminListingController {

    private final AdminListingService adminListingService;

    @Operation(summary = "매물 신고 목록 조회", description = "접수된 매물 신고 목록을 조회합니다. 신고가 없으면 빈 배열을 반환합니다.")
    @GetMapping("/reports")
    public ApiResponse<List<ReportResponse>> getListingReports() {
        return ApiResponse.ok(adminListingService.getListingReports());
    }

    @Operation(
            summary = "매물 숨김 처리",
            description = "신고 검토 후 매물을 숨김(HIDDEN) 처리합니다. 대상 매물이 없으면 404, 이미 숨김 처리된 매물이면 400을 반환합니다."
    )
    @PatchMapping("/listings/{id}/hide")
    public ApiResponse<Void> hideListing(@Parameter(description = "매물 ID") @PathVariable Long id) {
        adminListingService.hideListing(id);
        return ApiResponse.ok("매물이 숨김 처리되었습니다.");
    }
}
