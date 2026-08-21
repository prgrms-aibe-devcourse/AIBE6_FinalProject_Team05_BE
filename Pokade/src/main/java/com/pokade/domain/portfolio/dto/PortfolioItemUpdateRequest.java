package com.pokade.domain.portfolio.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public record PortfolioItemUpdateRequest(
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        Integer quantity,

        @Min(value = 0, message = "취득가는 0 이상이어야 합니다.")
        Integer acquiredPrice,

        LocalDateTime acquiredAt
) {
}
