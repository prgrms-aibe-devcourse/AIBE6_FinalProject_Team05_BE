package com.pokade.domain.inquiry.controller;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
import com.pokade.domain.inquiry.dto.request.InquiryUpdateRequest;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.service.InquiryService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "문의", description = "1:1 문의 접수 및 내 문의 내역 조회 API")
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(
            summary = "문의 접수",
            description = "제목·내용·카테고리와 첨부 이미지를 함께 보내 문의를 접수합니다(multipart/form-data). "
                    + "이미지는 선택이며 최대 3장, 장당 5MB 이하의 JPEG·PNG만 허용합니다. 접수되면 관리자에게 "
                    + "알림이 발송됩니다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InquiryResponse> createInquiry(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestPart("request") InquiryCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.ok("문의가 접수되었습니다.", inquiryService.createInquiry(userId, request, images));
    }

    @Operation(
            summary = "내 문의 내역 조회",
            description = "로그인한 회원이 접수한 문의와 답변 상태를 조회합니다."
    )
    @GetMapping("/me")
    public ApiResponse<List<InquiryResponse>> getMyInquiries(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(inquiryService.getMyInquiries(userId));
    }

    @PatchMapping("/{id}")
    public ApiResponse<InquiryResponse> updateInquiry(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody InquiryUpdateRequest request) {
        return ApiResponse.ok("문의가 수정되었습니다.", inquiryService.updateInquiry(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteInquiry(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        inquiryService.deleteInquiry(userId, id);
        return ApiResponse.ok("문의가 삭제되었습니다.");
    }
}
