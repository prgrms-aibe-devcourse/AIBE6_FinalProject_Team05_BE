package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.service.AdminListingService;
import com.pokade.domain.report.dto.ReportResponse;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminListingController {

    private final AdminListingService adminListingService;

    @GetMapping("/reports")
    public ApiResponse<List<ReportResponse>> getListingReports() {
        return ApiResponse.ok(adminListingService.getListingReports());
    }

    @PatchMapping("/listings/{id}/hide")
    public ApiResponse<Void> hideListing(@PathVariable Long id) {
        adminListingService.hideListing(id);
        return ApiResponse.ok("매물이 숨김 처리되었습니다.");
    }
}
