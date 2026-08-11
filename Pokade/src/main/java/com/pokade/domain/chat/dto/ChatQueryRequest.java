package com.pokade.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatQueryRequest(
        @NotBlank(message = "sessionId는 필수입니다.")
        String sessionId,

        @NotBlank(message = "message는 필수입니다.")
        String message
) {
}
