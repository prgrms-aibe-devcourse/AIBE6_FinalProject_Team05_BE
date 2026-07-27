package com.pokade.domain.sync.client;

import java.util.List;

import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.CardPriceDto;
import com.pokade.domain.sync.client.dto.CardVariantDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;

public interface ScrydexClient {

    List<ExpansionDto> fetchExpansions();

    List<CardDto> fetchCards(String expansionId);

    List<CardVariantDto> fetchCardVariants(String cardId);

    List<CardPriceDto> fetchCardPrices(String variantId);
}
