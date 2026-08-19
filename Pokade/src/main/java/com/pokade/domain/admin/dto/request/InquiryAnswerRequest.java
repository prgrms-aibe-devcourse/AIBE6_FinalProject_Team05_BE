package com.pokade.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InquiryAnswerRequest(
        @NotBlank(message = "답변 내용은 필수입니다.")
        String content
) {
}
