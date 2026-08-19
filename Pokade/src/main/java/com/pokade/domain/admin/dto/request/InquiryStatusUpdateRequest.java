package com.pokade.domain.admin.dto.request;

import com.pokade.domain.inquiry.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;

public record InquiryStatusUpdateRequest(
        @NotNull(message = "상태 값은 필수입니다.")
        InquiryStatus status
) {
}
