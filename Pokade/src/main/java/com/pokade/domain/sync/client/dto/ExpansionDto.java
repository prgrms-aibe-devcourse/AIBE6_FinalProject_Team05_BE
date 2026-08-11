package com.pokade.domain.sync.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExpansionDto(
        String id,
        String name,
        String series,
        String code,
        Integer total,
        @JsonProperty("printed_total") Integer printedTotal,
        @JsonProperty("language_code") String languageCode,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("is_online_only") Boolean isOnlineOnly,
        String logo,
        String symbol,
        TranslationDto translation
) {
}
