package com.pokade.price.controller;

import com.pokade.price.dto.PriceSummaryResponse;
import com.pokade.price.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/{cardId}/summary")
    public PriceSummaryResponse getSummary(
            @PathVariable Long cardId,
            @RequestParam(required = false) Long variantId
    ) {
        return priceService.getSummary(cardId, variantId);
    }
}
