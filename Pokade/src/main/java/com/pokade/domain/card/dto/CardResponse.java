package com.pokade.card.dto;

import java.util.List;

import com.pokade.card.entity.Card;

public record CardResponse(
        Long id,
        String externalId,
        String name,
        String setName,
        String rarity,
        String supertype,
        List<String> types,
        String imageSmall,
        String imageMedium,
        String expansionId
) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getExternalId(),
                card.getName(),
                card.getSetName(),
                card.getRarity(),
                card.getSupertype(),
                card.getTypes(),
                card.getImageSmall(),
                card.getImageMedium(),
                card.getExpansion() != null ? card.getExpansion().getId() : null
        );
    }
}