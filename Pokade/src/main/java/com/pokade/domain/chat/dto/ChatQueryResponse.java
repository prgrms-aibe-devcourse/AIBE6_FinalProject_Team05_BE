package com.pokade.domain.chat.dto;

import java.util.List;

public record ChatQueryResponse(
        String sessionId,
        String answer,
        String disclaimer,
        List<ChatRankingItemResponse> rankingItems
) {
}
