package com.pokade.domain.sync.client.dto;

import java.util.List;

public record CardDto(
        String externalId,
        String name,
        String setName,
        String rarity,
        String supertype,
        List<String> types,
        String printedNumber,
        String expansionId
) {
}
