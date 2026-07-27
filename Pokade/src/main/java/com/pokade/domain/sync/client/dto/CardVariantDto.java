package com.pokade.domain.sync.client.dto;

public record CardVariantDto(
        String variantId,
        String cardExternalId,
        String variantName,
        boolean primary
) {
}
