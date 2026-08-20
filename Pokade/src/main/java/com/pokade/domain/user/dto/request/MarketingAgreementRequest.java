package com.pokade.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarketingAgreementRequest(
        @NotNull(message = "동의 여부는 필수입니다.")
        Boolean agreed
) {
}
