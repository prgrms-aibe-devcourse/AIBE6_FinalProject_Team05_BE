package com.pokade.domain.watchlist.service;

// variantId가 null이면 "대표 변형 관심"을 뜻한다(Watchlist.variantId 주석과 동일한 규칙,
// CardVariantRepository.findGradesByCardId의 COALESCE(variant_id, primary_id) 관례와도 일치) - #300.
//
// WatchlistListingAvailableNoticeListener(단건 - 이벤트 하나당 CardVariantRepository.findPrimaryVariantId
// 1회 조회)와 WatchlistListingNotifiedResetService(배치 - findPrimaryVariantIdsByCardIds로 여러 카드를
// 한 번에 조회해 Map으로 보관)는 대표 variant ID를 구하는 방법이 서로 다르다(단건 조회 성능 특성이 달라
// 하나로 통일하면 한쪽이 손해를 본다). 이 클래스는 그 차이를 신경 쓰지 않고, "이미 구해온 대표 variant
// ID"와 "이 워치리스트/매물이 가진 variantId"를 합치는 규칙만 공용화한다.
public final class WatchlistVariantResolver {

    private WatchlistVariantResolver() {
    }

    // primaryVariantId가 null이면(동기화 누락 등으로 대표 variant 정보 자체가 없는 경우) 그대로 null을
    // 반환한다 - Objects.requireNonNullElse는 폴백값이 null이면 NPE를 던져서 여기선 쓸 수 없다.
    public static Long resolveOrPrimary(Long variantId, Long primaryVariantId) {
        return variantId != null ? variantId : primaryVariantId;
    }
}
