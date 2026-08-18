package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.service.AdminInquiryService;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    @GetMapping
    public ApiResponse<List<InquiryResponse>> getInquiries() {
        return ApiResponse.ok(adminInquiryService.getInquiries());
    }

    @GetMapping("/{id}")
    public ApiResponse<InquiryResponse> getInquiry(@PathVariable Long id) {
        return ApiResponse.ok(adminInquiryService.getInquiry(id));
    }
}
