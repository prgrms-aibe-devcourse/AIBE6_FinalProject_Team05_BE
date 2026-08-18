package com.pokade.domain.watchlist.dto;

import jakarta.validation.constraints.Positive;

public record WatchlistUpdateRequest(
        @Positive(message = "targetBuyPrice는 0보다 커야 합니다.")
        Integer targetBuyPrice,

        @Positive(message = "targetSellPrice는 0보다 커야 합니다.")
        Integer targetSellPrice,

        // null/false 둘 다 "재알림 요청 없음"으로 취급 - Boolean.TRUE.equals()로 체크할 것(원시 boolean 언박싱 NPE 방지)
        Boolean resendNotification
) {
}
