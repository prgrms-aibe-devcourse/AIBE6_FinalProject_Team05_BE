package com.pokade.domain.card.dto;

import java.util.List;

import com.pokade.domain.card.entity.Card;

public record CardResponse(
        Long id,
        String externalId,
        String name,
        String nameKo,
        String languageCode,
        String setName,
        String rarity,
        String supertype,
        List<String> types,
        String imageSmall,
        String imageMedium,
        String expansionId,
        List<String> grades
) {

    public static CardResponse from(Card card, List<String> grades, String nameKo, List<String> types, String rarity) {
        return new CardResponse(
                card.getId(),
                card.getExternalId(),
                card.getName(),
                nameKo,
                card.getLanguageCode(),
                card.getSetName(),
                rarity,
                card.getSupertype(),
                types,
                card.getImageSmall(),
                card.getImageMedium(),
                card.getExpansion() != null ? card.getExpansion().getId() : null,
                grades
        );
    }
}