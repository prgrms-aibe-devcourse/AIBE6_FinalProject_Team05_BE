package com.pokade.domain.price.controller;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/{cardId}/summary")
    public ApiResponse<PriceSummaryResponse> getSummary(
            @PathVariable Long cardId,
            @RequestParam(required = false) Long variantId
    ) {
        return ApiResponse.ok(priceService.getSummary(cardId, variantId));
    }

    @GetMapping("/summaries")
    public ApiResponse<List<CardPriceSummaryResponse>> getSummaries(
            @RequestParam List<Long> cardIds,
            @RequestParam(required = false) ListingGrade grade,
            @RequestParam(required = false, defaultValue = "false") boolean includeRecentTradePrice
    ) {
        return ApiResponse.ok(priceService.getSummaries(cardIds, grade, includeRecentTradePrice));
    }

    @GetMapping("/{cardId}/trades")
    public ApiResponse<List<TradeSummaryResponse>> getRecentTrades(@PathVariable Long cardId) {
        return ApiResponse.ok(priceService.getRecentTrades(cardId));
    }

    @GetMapping("/{cardId}/chart")
    public ApiResponse<List<TradeSummaryResponse>> getPriceChart(
            @PathVariable Long cardId,
            @RequestParam String period
    ) {
        return ApiResponse.ok(priceService.getPriceChart(cardId, period));
    }

    @GetMapping("/{cardId}/stats")
    public ApiResponse<PriceStatsResponse> getStats(
            @PathVariable Long cardId,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) ListingGrade grade,
            @RequestParam(required = false) String period
    ) {
        return ApiResponse.ok(priceService.getStats(cardId, variantId, grade, period));
    }

    @GetMapping("/ranking")
    public ApiResponse<List<PriceRankingResponse>> getRanking(@RequestParam String type) {
        return ApiResponse.ok(priceService.getRanking(type));
    }
}
