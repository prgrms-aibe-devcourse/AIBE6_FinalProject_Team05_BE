package com.pokade.domain.sync.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardDto(
        String id,
        String name,
        String supertype,
        List<String> types,
        List<String> subtypes,
        String rarity,
        @JsonProperty("rarity_code") String rarityCode,
        String hp,
        String artist,
        @JsonProperty("national_pokedex_numbers") List<Integer> nationalPokedexNumbers,
        @JsonProperty("printed_number") String printedNumber,
        @JsonProperty("evolves_from") List<String> evolvesFrom,
        List<ImageDto> images,
        ExpansionDto expansion,
        @JsonProperty("expansion_sort_order") Integer expansionSortOrder,
        @JsonProperty("language_code") String languageCode,
        List<CardVariantDto> variants
) {
}
