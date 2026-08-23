package com.pokade.domain.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TradeReadyRequest(
        @NotNull(message = "listingId는 필수입니다.")
        Long listingId,

        @NotNull(message = "pointsToUse는 필수입니다.")
        @Min(value = 0, message = "pointsToUse는 0 이상이어야 합니다.")
        Integer pointsToUse,

        @NotBlank(message = "받는사람 이름은 필수입니다.")
        String recipientName,

        @NotBlank(message = "받는사람 전화번호는 필수입니다.")
        String recipientPhone,

        @NotBlank(message = "받는사람 주소는 필수입니다.")
        String recipientAddress
) {
}
