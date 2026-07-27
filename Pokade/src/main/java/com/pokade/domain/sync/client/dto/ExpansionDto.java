package com.pokade.domain.sync.client.dto;

import java.time.LocalDate;

public record ExpansionDto(
        String id,
        String name,
        String series,
        String code,
        Integer total,
        String languageCode,
        LocalDate releaseDate
) {
}
