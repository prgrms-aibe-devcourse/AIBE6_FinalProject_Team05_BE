package com.pokade.domain.card.dto;

import java.util.List;

public record CardFacetsResponse(
        List<FacetOption> types,
        List<FacetOption> rarities,
        List<ExpansionFacet> expansions
) {

    // count: 이 값(타입/레어도)을 가진 카드 수 - 전체 기준 고정 집계(다른 필터 선택과 무관, #263).
    public record FacetOption(String value, long count) {
    }

    public record ExpansionFacet(String id, String name, String series, long count) {
    }

    public static CardFacetsResponse of(List<FacetOption> types, List<FacetOption> rarities, List<ExpansionFacet> expansions) {
        return new CardFacetsResponse(types, rarities, expansions);
    }
}
