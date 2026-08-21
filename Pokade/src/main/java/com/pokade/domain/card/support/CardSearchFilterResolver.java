package com.pokade.domain.card.support;

import java.util.List;

/**
 * 카드 검색 필터 5종(types/rarity/language/price) 중 types/rarity/language/price 유무 판단과
 * IN절·overlap 바인딩용 안전값 대체를 공용화한다(#308 후속 리팩터링) - {@code CardRepository.search()}
 * 기본 메서드와 {@code CardQueryService}의 키워드+필터 결합 검색이 완전히 동일한 규칙(blank 제거 →
 * hasX 판단 → 안전값 대체)을 각자 구현하고 있던 걸 통합했다. 두 클래스가 서로 다른 패키지
 * (domain.card.repository / domain.card.service)라 이 support 패키지에 둔다 -
 * {@link CardTypeEnResolver}/{@link CardRarityResolver}처럼 여러 레이어가 참조하는 순수 static
 * 유틸이라는 점도 같은 패턴이다.
 */
public final class CardSearchFilterResolver {

    private CardSearchFilterResolver() {
    }

    /**
     * types는 {@code c.types && :types}(overlap 연산자, GIN 인덱스 활용)로 바인딩되므로 String[]로,
     * rarity/language는 {@code IN (:x)}로 바인딩되므로 "필터 없음"일 때 List.of("")(무의미한 값으로
     * 채워 조건 자체가 항상 스킵되게 하는 트릭)로 반환한다 - 두 호출부(CardRepository.search(),
     * CardQueryService)가 원래 각자 이렇게 처리하던 것과 완전히 동일하다.
     */
    public record Resolved(boolean hasTypes, String[] types, boolean hasRarities, List<String> rarities,
                            boolean hasLanguages, List<String> languages, boolean hasPrice) {
    }

    public static Resolved resolve(List<String> types, List<String> rarities, List<String> languages,
                                    Integer minPrice, Integer maxPrice) {
        // 빈 문자열("")이 섞여 들어오면 실제 필터 조건 없이 IN ('') 비교만 남아 매칭이 전혀 안 되므로
        // hasTypes/hasRarities/hasLanguages 판단 전에 제거한다.
        List<String> filteredTypes = types == null ? null
                : types.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredRarities = rarities == null ? null
                : rarities.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredLanguages = languages == null ? null
                : languages.stream().filter(v -> v != null && !v.isBlank()).toList();

        boolean hasTypes = filteredTypes != null && !filteredTypes.isEmpty();
        boolean hasRarities = filteredRarities != null && !filteredRarities.isEmpty();
        boolean hasLanguages = filteredLanguages != null && !filteredLanguages.isEmpty();
        boolean hasPrice = minPrice != null || maxPrice != null;

        String[] safeTypes = (hasTypes ? filteredTypes : List.<String>of()).toArray(String[]::new);
        List<String> safeRarities = hasRarities ? filteredRarities : List.of("");
        List<String> safeLanguages = hasLanguages ? filteredLanguages : List.of("");

        return new Resolved(hasTypes, safeTypes, hasRarities, safeRarities, hasLanguages, safeLanguages, hasPrice);
    }
}
