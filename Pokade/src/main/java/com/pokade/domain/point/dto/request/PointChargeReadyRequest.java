package com.pokade.domain.point.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PointChargeReadyRequest(
        @NotNull(message = "충전 금액은 필수입니다.")
        @Min(value = 1000, message = "충전 금액은 1,000원 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "충전 금액은 1,000,000원을 초과할 수 없습니다.")
        Integer amount
) {
}
