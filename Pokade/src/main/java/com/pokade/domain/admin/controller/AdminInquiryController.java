package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.dto.request.InquiryAnswerRequest;
import com.pokade.domain.admin.dto.request.InquiryStatusUpdateRequest;
import com.pokade.domain.admin.service.AdminInquiryService;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.InquiryCategory;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "관리자 - 문의", description = "접수된 1:1 문의 조회 및 답변/상태 처리 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminInquiryService adminInquiryService;

    @Operation(
            summary = "문의 목록 조회",
            description = "접수된 문의를 카테고리로 필터링해 페이징 조회합니다. 카테고리를 생략하면 전체를 조회합니다."
    )
    @GetMapping
    public ApiResponse<Page<InquiryResponse>> getInquiries(
            @Parameter(description = "문의 카테고리") @RequestParam(required = false) InquiryCategory category,
            @PageableDefault(size = DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.ok(adminInquiryService.getInquiries(category, pageable));
    }

    @Operation(
            summary = "문의 상세 조회",
            description = "문의 한 건의 상세 내용과 첨부 이미지를 조회합니다."
    )
    @GetMapping("/{id}")
    public ApiResponse<InquiryResponse> getInquiry(
            @Parameter(description = "문의 ID") @PathVariable Long id) {
        return ApiResponse.ok(adminInquiryService.getInquiry(id));
    }

    @Operation(
            summary = "문의 상태 변경",
            description = "문의 처리 상태를 변경합니다. 처리 완료로 새로 바뀌는 경우에만 문의자에게 알림이 발송됩니다."
    )
    @PatchMapping("/{id}/status")
    public ApiResponse<InquiryResponse> updateStatus(
            @Parameter(description = "문의 ID") @PathVariable Long id,
            @Valid @RequestBody InquiryStatusUpdateRequest request) {
        return ApiResponse.ok(adminInquiryService.updateStatus(id, request.status()));
    }

    @Operation(
            summary = "문의 답변 등록",
            description = "문의에 답변을 등록합니다. 첫 답변일 때만 문의자에게 알림이 발송되며, "
                    + "이후 답변을 수정해도 알림은 다시 가지 않습니다."
    )
    @PatchMapping("/{id}/answer")
    public ApiResponse<InquiryResponse> answerInquiry(
            @Parameter(description = "문의 ID") @PathVariable Long id,
            @Valid @RequestBody InquiryAnswerRequest request) {
        return ApiResponse.ok("답변이 등록되었습니다.", adminInquiryService.answerInquiry(id, request.content()));
    }
}
