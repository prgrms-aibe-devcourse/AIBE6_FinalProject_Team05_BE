package com.pokade.domain.inquiry.dto.request;

import com.pokade.domain.inquiry.entity.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryUpdateRequest(
        @NotNull(message = "문의 유형은 필수입니다.")
        InquiryCategory category,
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다.")
        String title,
        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
