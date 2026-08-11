package com.pokade.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatQueryRequest(
        @NotBlank(message = "sessionId는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "sessionId는 UUID 형식이어야 합니다."
        )
        String sessionId,

        @NotBlank(message = "message는 필수입니다.")
        @Size(max = 500, message = "message는 최대 500자까지 입력할 수 있습니다.")
        String message
) {
}
