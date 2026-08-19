package com.pokade.domain.chat.dto;

public record ChatHistoryImportResponse(
        int imported,
        int skipped
) {
}
