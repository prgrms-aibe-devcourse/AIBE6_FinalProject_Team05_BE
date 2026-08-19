package com.pokade.domain.card.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;

public record CardDetailResponse(
        Long id,
        String externalId,
        String name,
        String nameKo,
        String languageCode,
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

    public static CardDetailResponse of(Card card, List<CardVariant> variants, Map<Long, List<String>> gradesByVariantId, String nameKo, List<String> types, String rarity) {
        return new CardDetailResponse(
                card.getId(),
                card.getExternalId(),
                card.getName(),
                nameKo,
                card.getLanguageCode(),
                card.getSetName(),
                rarity,
                card.getSupertype(),
                types,
                card.getArtist(),
                card.getPrintedNumber(),
                card.getImageSmall(),
                card.getImageMedium(),
                card.getImageLarge(),
                ExpansionSummary.from(card.getExpansion()),
                variants.stream()
                        .map(variant -> VariantSummary.from(variant, gradesByVariantId.getOrDefault(variant.getId(), List.of())))
                        .toList()
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
            String imageLarge,
            List<String> grades
    ) {

        public static VariantSummary from(CardVariant variant, List<String> grades) {
            return new VariantSummary(
                    variant.getId(),
                    variant.getVariantName(),
                    variant.isPrimary(),
                    variant.getImageSmall(),
                    variant.getImageLarge(),
                    grades
            );
        }
    }
}
