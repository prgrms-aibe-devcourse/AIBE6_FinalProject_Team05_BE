package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.dto.request.InquiryStatusUpdateRequest;
import com.pokade.domain.admin.service.AdminInquiryService;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.InquiryCategory;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminInquiryService adminInquiryService;

    @GetMapping
    public ApiResponse<Page<InquiryResponse>> getInquiries(
            @RequestParam(required = false) InquiryCategory category,
            @PageableDefault(size = DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.ok(adminInquiryService.getInquiries(category, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<InquiryResponse> getInquiry(@PathVariable Long id) {
        return ApiResponse.ok(adminInquiryService.getInquiry(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<InquiryResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody InquiryStatusUpdateRequest request) {
        return ApiResponse.ok(adminInquiryService.updateStatus(id, request.status()));
    }
}
