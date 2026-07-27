package com.pokade.domain.card.dto;

import java.time.LocalDate;
import java.util.List;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;

public record CardDetailResponse(
        Long id,
        String externalId,
        String name,
        String setName,
        String rarity,
        String supertype,
        List<String> types,
        String artist,
        String printedNumber,
        String imageSmall,
        String imageMedium,
        String imageLarge,
        ExpansionSummary expansion,
        List<VariantSummary> variants
) {

    public static CardDetailResponse of(Card card, List<CardVariant> variants) {
        return new CardDetailResponse(
                card.getId(),
                card.getExternalId(),
                card.getName(),
                card.getSetName(),
                card.getRarity(),
                card.getSupertype(),
                card.getTypes(),
                card.getArtist(),
                card.getPrintedNumber(),
                card.getImageSmall(),
                card.getImageMedium(),
                card.getImageLarge(),
                ExpansionSummary.from(card.getExpansion()),
                variants.stream().map(VariantSummary::from).toList()
        );
    }

    public record ExpansionSummary(
            String id,
            String name,
            String series,
            String code,
            Integer total,
            LocalDate releaseDate,
            String logo,
            String symbol
    ) {

        public static ExpansionSummary from(Expansion expansion) {
            if (expansion == null) {
                return null;
            }
            return new ExpansionSummary(
                    expansion.getId(),
                    expansion.getName(),
                    expansion.getSeries(),
                    expansion.getCode(),
                    expansion.getTotal(),
                    expansion.getReleaseDate(),
                    expansion.getLogo(),
                    expansion.getSymbol()
            );
        }
    }

    public record VariantSummary(
            Long id,
            String variantName,
            boolean primary,
            String imageSmall,
            String imageLarge
    ) {

        public static VariantSummary from(CardVariant variant) {
            return new VariantSummary(
                    variant.getId(),
                    variant.getVariantName(),
                    variant.isPrimary(),
                    variant.getImageSmall(),
                    variant.getImageLarge()
            );
        }
    }
}
