package com.pokade.domain.inquiry.controller;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.service.InquiryService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InquiryResponse> createInquiry(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestPart("request") InquiryCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.ok("문의가 접수되었습니다.", inquiryService.createInquiry(userId, request, images));
    }

    @GetMapping("/me")
    public ApiResponse<List<InquiryResponse>> getMyInquiries(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(inquiryService.getMyInquiries(userId));
    }
}
