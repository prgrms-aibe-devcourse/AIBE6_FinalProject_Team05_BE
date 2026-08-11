package com.pokade.domain.chat.dto;

public record ChatQueryResponse(
        String sessionId,
        String answer,
        String disclaimer
) {
}
