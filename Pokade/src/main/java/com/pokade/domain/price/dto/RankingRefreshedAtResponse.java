package com.pokade.domain.price.dto;

import java.time.LocalDateTime;

// GET /api/prices/ranking/refreshed-at 응답 - 아직 한 번도 배치가 돈 적 없으면(배포 직후 등) null.
public record RankingRefreshedAtResponse(LocalDateTime refreshedAt) {
}
