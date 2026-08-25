package com.pokade.domain.portfolio.dto;

// AI 진단 결과 등록(FR-AI-04) 시 카드를 직접 지정하기 위한 선택적 바디.
// AI가 카드를 인식하지 못했거나(cardId/visionCardId 둘 다 null), 잘못 인식한 경우
// 사용자가 직접 고른 카드로 덮어쓰기 위해 사용한다. 둘 다 null이면 기존 AI 인식 결과를 그대로 쓴다.
public record PortfolioFromGradeRequest(
        Long cardId,
        Long variantId
) {
}
