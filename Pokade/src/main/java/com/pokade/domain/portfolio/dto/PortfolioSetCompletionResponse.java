package com.pokade.domain.portfolio.dto;

import java.math.BigDecimal;

// GET /api/portfolio/set-completion 응답 항목 - 세트 하나에 대한 수집 완성도.
// ownedCount는 해당 세트에서 보유 중인 "서로 다른 카드" 개수(수량은 무시), totalCount는
// 그 세트의 공식 전체 카드 수(Expansion.total)다.
public record PortfolioSetCompletionResponse(
        String expansionId,
        String setName,
        int ownedCount,
        int totalCount,
        BigDecimal completionRate
) {
}
