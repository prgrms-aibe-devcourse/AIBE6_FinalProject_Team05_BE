package com.pokade.domain.chat.dto;

import com.pokade.domain.chat.entity.ChatMessage;

import java.time.LocalDateTime;

public record ChatHistoryResponse(
        String role,
        String content,
        LocalDateTime createdAt
) {

    public static ChatHistoryResponse from(ChatMessage message) {
        return new ChatHistoryResponse(message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
