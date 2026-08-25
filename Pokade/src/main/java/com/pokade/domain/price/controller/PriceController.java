package com.pokade.domain.price.controller;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.dto.BuyOfferOrderbookEntryResponse;
import com.pokade.domain.price.dto.CardPricePointResponse;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.MarketOverviewResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.RankingRefreshedAtResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "시세", description = "카드 시세 요약·호가·체결 내역·차트·랭킹 조회 API")
@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @Operation(
            summary = "카드 시세 요약",
            description = "카드 한 장의 최저 판매가·최고 구매입찰가 등 시세 요약을 조회합니다. variantId를 주면 "
                    + "해당 배리언트 기준으로 좁혀서 계산합니다. 비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/{cardId}/summary")
    public ApiResponse<PriceSummaryResponse> getSummary(
            @Parameter(description = "카드 ID") @PathVariable Long cardId,
            @Parameter(description = "배리언트 ID") @RequestParam(required = false) Long variantId
    ) {
        return ApiResponse.ok(priceService.getSummary(cardId, variantId));
    }

    @Operation(
            summary = "카드 시세 요약 일괄 조회",
            description = "여러 카드의 시세 요약을 한 번에 조회합니다. 목록·그리드 화면에서 카드마다 요청을 보내지 "
                    + "않도록 만든 엔드포인트입니다. 비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/summaries")
    public ApiResponse<List<CardPriceSummaryResponse>> getSummaries(
            @Parameter(description = "카드 ID 목록") @RequestParam List<Long> cardIds,
            @Parameter(description = "등급 필터") @RequestParam(required = false) ListingGrade grade,
            @Parameter(description = "최근 체결가 포함 여부") @RequestParam(required = false, defaultValue = "false") boolean includeRecentTradePrice
    ) {
        return ApiResponse.ok(priceService.getSummaries(cardIds, grade, includeRecentTradePrice));
    }

    @Operation(
            summary = "구매입찰 호가창 조회",
            description = "카드에 걸린 구매입찰을 가격대별로 묶어 호가창 형태로 조회합니다."
    )
    @GetMapping("/{cardId}/buy-offers")
    public ApiResponse<List<BuyOfferOrderbookEntryResponse>> getBuyOfferOrderbook(
            @Parameter(description = "카드 ID") @PathVariable Long cardId,
            @Parameter(description = "배리언트 ID") @RequestParam(required = false) Long variantId,
            @Parameter(description = "등급 필터") @RequestParam(required = false) ListingGrade grade
    ) {
        return ApiResponse.ok(priceService.getBuyOfferOrderbook(cardId, variantId, grade));
    }

    @Operation(
            summary = "최근 체결 내역 조회",
            description = "카드의 최근 체결(거래 완료) 내역을 조회합니다. 비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/{cardId}/trades")
    public ApiResponse<List<TradeSummaryResponse>> getRecentTrades(
            @Parameter(description = "카드 ID") @PathVariable Long cardId) {
        return ApiResponse.ok(priceService.getRecentTrades(cardId));
    }

    @Operation(
            summary = "시세 차트 조회",
            description = "지정한 기간의 체결가 시계열을 조회합니다. period는 필수이며 7d·30d·90d·180d만 "
                    + "허용하고, 그 외 값이면 실패합니다. 시세 통계(stats)의 기간 값과는 구성이 다르므로 혼용하지 않습니다."
    )
    @GetMapping("/{cardId}/chart")
    public ApiResponse<List<TradeSummaryResponse>> getPriceChart(
            @Parameter(description = "카드 ID") @PathVariable Long cardId,
            @Parameter(description = "조회 기간 (7d, 30d, 90d, 180d)") @RequestParam String period
    ) {
        return ApiResponse.ok(priceService.getPriceChart(cardId, period));
    }

    @Operation(
            summary = "시세 통계 조회",
            description = "기간 대비 등락 등 시세 통계를 조회합니다. grade와 period를 모두 생략하면 체결 이력 "
                    + "기반으로 계산하고, 하나라도 지정하면 동기화된 카드 시세 테이블의 등락률을 조회합니다. "
                    + "period는 1d·7d·14d·30d·90d·180d만 허용하며 생략 시 7d로 처리합니다."
    )
    @GetMapping("/{cardId}/stats")
    public ApiResponse<PriceStatsResponse> getStats(
            @Parameter(description = "카드 ID") @PathVariable Long cardId,
            @Parameter(description = "배리언트 ID") @RequestParam(required = false) Long variantId,
            @Parameter(description = "등급 필터") @RequestParam(required = false) ListingGrade grade,
            @Parameter(description = "조회 기간 (1d, 7d, 14d, 30d, 90d, 180d)") @RequestParam(required = false) String period
    ) {
        return ApiResponse.ok(priceService.getStats(cardId, variantId, grade, period));
    }

    @Operation(
            summary = "등급별 시세 차트 조회",
            description = "특정 등급 기준의 시세 추이를 조회합니다. grade는 필수입니다."
    )
    @GetMapping("/{cardId}/grade-chart")
    public ApiResponse<List<CardPricePointResponse>> getGradeChart(
            @Parameter(description = "카드 ID") @PathVariable Long cardId,
            @Parameter(description = "배리언트 ID") @RequestParam(required = false) Long variantId,
            @Parameter(description = "등급") @RequestParam ListingGrade grade
    ) {
        return ApiResponse.ok(priceService.getGradeChart(cardId, variantId, grade));
    }

    @Operation(
            summary = "시세 랭킹 조회",
            description = "급등(rise) 또는 급락(fall) 랭킹을 조회합니다. 배치로 미리 계산된 결과를 반환하며, "
                    + "그 외 type 값이면 실패합니다. 비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/ranking")
    public ApiResponse<List<PriceRankingResponse>> getRanking(
            @Parameter(description = "랭킹 종류 (rise, fall)") @RequestParam String type) {
        return ApiResponse.ok(priceService.getRanking(type));
    }

    @Operation(
            summary = "랭킹 갱신 시각 조회",
            description = "해당 랭킹이 마지막으로 계산된 시각을 조회합니다. 화면에 기준 시각을 표시할 때 사용합니다."
    )
    @GetMapping("/ranking/refreshed-at")
    public ApiResponse<RankingRefreshedAtResponse> getRankingRefreshedAt(
            @Parameter(description = "랭킹 종류 (rise, fall)") @RequestParam String type) {
        return ApiResponse.ok(new RankingRefreshedAtResponse(priceService.getRankingRefreshedAt(type)));
    }

    @Operation(
            summary = "마켓 요약 조회",
            description = "메인 화면 상단에 노출할 전체 시장 요약 지표를 조회합니다. "
                    + "비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/market-overview")
    public ApiResponse<MarketOverviewResponse> getMarketOverview() {
        return ApiResponse.ok(priceService.getMarketOverview());
    }
}
