package com.pokade.domain.sync.client.dto;

import java.util.List;

public record CardVariantDto(
        String name,
        List<ImageDto> images,
        List<CardPriceDto> prices
) {
}
