package com.pokade.domain.price.controller;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriceService priceService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @Test
    void 체결_내역이_있으면_200과_최신순_목록을_반환한다() throws Exception {
        List<TradeSummaryResponse> trades = List.of(
                new TradeSummaryResponse(LocalDateTime.now().minusDays(1), 5000000, ListingGrade.PSA10),
                new TradeSummaryResponse(LocalDateTime.now().minusDays(3), 3000000, ListingGrade.S),
                new TradeSummaryResponse(LocalDateTime.now().minusDays(10), 2500000, null)
        );
        given(priceService.getRecentTrades(1L)).willReturn(trades);

        mockMvc.perform(get("/api/prices/1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].grade").value("PSA10"))
                .andExpect(jsonPath("$.data[0].price").value(5000000))
                .andExpect(jsonPath("$.data[2].grade").value(nullValue()));
    }

    @Test
    void 체결_이력이_없으면_200과_빈_목록을_반환한다() throws Exception {
        given(priceService.getRecentTrades(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/prices/1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 존재하지_않는_카드면_404를_반환한다() throws Exception {
        given(priceService.getRecentTrades(999L))
                .willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/prices/999/trades"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    void 차트_데이터가_있으면_200과_오래된순_목록을_반환한다() throws Exception {
        List<TradeSummaryResponse> trades = List.of(
                new TradeSummaryResponse(LocalDateTime.now().minusDays(10), 2500000, null),
                new TradeSummaryResponse(LocalDateTime.now().minusDays(3), 3000000, ListingGrade.S),
                new TradeSummaryResponse(LocalDateTime.now().minusDays(1), 5000000, ListingGrade.PSA10)
        );
        given(priceService.getPriceChart(1L, "30d")).willReturn(trades);

        mockMvc.perform(get("/api/prices/1/chart").param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[2].grade").value("PSA10"));
    }

    @Test
    void 차트_체결_이력이_없으면_200과_빈_목록을_반환한다() throws Exception {
        given(priceService.getPriceChart(1L, "90d")).willReturn(List.of());

        mockMvc.perform(get("/api/prices/1/chart").param("period", "90d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 차트_조회시_존재하지_않는_카드면_404를_반환한다() throws Exception {
        given(priceService.getPriceChart(999L, "1y"))
                .willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/prices/999/chart").param("period", "1y"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    void 잘못된_period_값이면_400을_반환한다() throws Exception {
        given(priceService.getPriceChart(1L, "invalid"))
                .willThrow(new BusinessException(ErrorCode.INVALID_PERIOD));

        mockMvc.perform(get("/api/prices/1/chart").param("period", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void period_파라미터가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/prices/1/chart"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 배치_요약_조회시_cardIds에_해당하는_요약_목록을_반환한다() throws Exception {
        List<CardPriceSummaryResponse> summaries = List.of(
                new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW"),
                new CardPriceSummaryResponse(2L, null, 2000000, null, "KRW")
        );
        given(priceService.getSummaries(List.of(1L, 2L), null, false)).willReturn(summaries);

        mockMvc.perform(get("/api/prices/summaries").param("cardIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].cardId").value(1))
                .andExpect(jsonPath("$.data[0].buyPrice").value(3000000))
                .andExpect(jsonPath("$.data[0].sellPrice").value(nullValue()))
                .andExpect(jsonPath("$.data[1].cardId").value(2))
                .andExpect(jsonPath("$.data[1].sellPrice").value(2000000));
    }

    @Test
    void 배치_요약_조회시_cardIds가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/prices/summaries"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 배치_요약_조회시_상한을_넘으면_400을_반환한다() throws Exception {
        given(priceService.getSummaries(any(), any(), anyBoolean()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "cardIds는 최대 100개까지 조회할 수 있습니다."));

        mockMvc.perform(get("/api/prices/summaries").param("cardIds", "1,2,3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 배치_요약_조회시_grade를_지정하면_해당_등급의_요약만_반환한다() throws Exception {
        List<CardPriceSummaryResponse> summaries = List.of(
                new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW")
        );
        given(priceService.getSummaries(List.of(1L), ListingGrade.S, false)).willReturn(summaries);

        mockMvc.perform(get("/api/prices/summaries").param("cardIds", "1").param("grade", "S"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].buyPrice").value(3000000));
    }

    @Test
    void 배치_요약_조회시_잘못된_grade_값이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/prices/summaries").param("cardIds", "1").param("grade", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 배치_요약_조회시_includeRecentTradePrice가_true면_최근_체결가를_함께_반환한다() throws Exception {
        List<CardPriceSummaryResponse> summaries = List.of(
                new CardPriceSummaryResponse(1L, null, null, 2950000, "KRW")
        );
        given(priceService.getSummaries(List.of(1L), null, true)).willReturn(summaries);

        mockMvc.perform(get("/api/prices/summaries").param("cardIds", "1").param("includeRecentTradePrice", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].buyPrice").value(nullValue()))
                .andExpect(jsonPath("$.data[0].recentTradePrice").value(2950000));
    }

    @Test
    void 배치_요약_조회시_includeRecentTradePrice_미지정이면_최근_체결가없이_조회한다() throws Exception {
        List<CardPriceSummaryResponse> summaries = List.of(
                new CardPriceSummaryResponse(1L, 3000000, null, null, "KRW")
        );
        given(priceService.getSummaries(List.of(1L), null, false)).willReturn(summaries);

        mockMvc.perform(get("/api/prices/summaries").param("cardIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].recentTradePrice").value(nullValue()));
    }

    @Test
    void 시세_등락률과_거래량을_조회하면_200과_값을_반환한다() throws Exception {
        given(priceService.getStats(1L, null, null, null))
                .willReturn(new PriceStatsResponse(new BigDecimal("6.01"), 170000L, 1L));

        mockMvc.perform(get("/api/prices/1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeRate").value(6.01))
                .andExpect(jsonPath("$.data.changeAmount").value(170000))
                .andExpect(jsonPath("$.data.volume").value(1));
    }

    @Test
    void 시세_통계_조회시_존재하지_않는_카드면_404를_반환한다() throws Exception {
        given(priceService.getStats(999L, null, null, null))
                .willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/prices/999/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    void 시세_통계_조회시_대표_변형이_없으면_404를_반환한다() throws Exception {
        given(priceService.getStats(1L, null, null, null))
                .willThrow(new BusinessException(ErrorCode.PRIMARY_VARIANT_NOT_FOUND));

        mockMvc.perform(get("/api/prices/1/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRIMARY_VARIANT_NOT_FOUND"));
    }

    @Test
    void 시세_통계_조회시_grade와_period를_지정하면_그대로_서비스에_전달한다() throws Exception {
        given(priceService.getStats(1L, null, ListingGrade.PSA10, "30d"))
                .willReturn(new PriceStatsResponse(new BigDecimal("5.50"), null, 0L));

        mockMvc.perform(get("/api/prices/1/stats").param("grade", "PSA10").param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeRate").value(5.50))
                .andExpect(jsonPath("$.data.changeAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.volume").value(0));
    }

    @Test
    void 시세_통계_조회시_잘못된_period_값이면_400을_반환한다() throws Exception {
        given(priceService.getStats(1L, null, ListingGrade.S, "3d"))
                .willThrow(new BusinessException(ErrorCode.INVALID_PERIOD));

        mockMvc.perform(get("/api/prices/1/stats").param("grade", "S").param("period", "3d"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void 급등_랭킹을_조회하면_200과_변동률_상위_목록을_반환한다() throws Exception {
        List<PriceRankingResponse> ranking = List.of(
                new PriceRankingResponse(2L, "Charizard-GX", "img-2", 340000L, new BigDecimal("13.33"), 40000L),
                new PriceRankingResponse(1L, "Blastoise", "img-1", 900000L, new BigDecimal("12.5"), 100000L)
        );
        given(priceService.getRanking("rise")).willReturn(ranking);

        mockMvc.perform(get("/api/prices/ranking").param("type", "rise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].cardName").value("Charizard-GX"))
                .andExpect(jsonPath("$.data[0].changeRate").value(13.33))
                .andExpect(jsonPath("$.data[1].cardName").value("Blastoise"));
    }

    @Test
    void 급락_랭킹을_조회하면_200과_변동률_하위_목록을_반환한다() throws Exception {
        List<PriceRankingResponse> ranking = List.of(
                new PriceRankingResponse(4L, "Charizard ex", "img-4", 430000L, new BigDecimal("-14"), -70000L)
        );
        given(priceService.getRanking("fall")).willReturn(ranking);

        mockMvc.perform(get("/api/prices/ranking").param("type", "fall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cardName").value("Charizard ex"))
                .andExpect(jsonPath("$.data[0].changeRate").value(-14));
    }

    @Test
    void 랭킹_조회시_배치_미실행_등으로_결과가_없으면_200과_빈_목록을_반환한다() throws Exception {
        given(priceService.getRanking("rise")).willReturn(List.of());

        mockMvc.perform(get("/api/prices/ranking").param("type", "rise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 랭킹_조회시_type_파라미터가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/prices/ranking"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 랭킹_조회시_잘못된_type_값이면_400을_반환한다() throws Exception {
        given(priceService.getRanking("up"))
                .willThrow(new BusinessException(ErrorCode.INVALID_RANKING_TYPE));

        mockMvc.perform(get("/api/prices/ranking").param("type", "up"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANKING_TYPE"));
    }
}
