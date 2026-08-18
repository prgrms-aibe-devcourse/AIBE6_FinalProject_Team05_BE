package com.pokade.domain.inquiry.controller;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.service.InquiryService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    public ApiResponse<InquiryResponse> createInquiry(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InquiryCreateRequest request) {
        return ApiResponse.ok("문의가 접수되었습니다.", inquiryService.createInquiry(userId, request));
    }

    @GetMapping("/me")
    public ApiResponse<List<InquiryResponse>> getMyInquiries(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(inquiryService.getMyInquiries(userId));
    }
}
