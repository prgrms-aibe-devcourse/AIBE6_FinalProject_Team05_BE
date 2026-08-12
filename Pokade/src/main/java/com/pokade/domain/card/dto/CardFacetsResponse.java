package com.pokade.domain.card.dto;

import java.util.List;

public record CardFacetsResponse(
        List<String> types,
        List<String> rarities,
        List<ExpansionFacet> expansions
) {

    public record ExpansionFacet(String id, String name) {
    }

    public static CardFacetsResponse of(List<String> types, List<String> rarities, List<ExpansionFacet> expansions) {
        return new CardFacetsResponse(types, rarities, expansions);
    }
}
