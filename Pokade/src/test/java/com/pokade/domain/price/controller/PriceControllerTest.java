package com.pokade.domain.price.controller;

import com.pokade.domain.listing.ListingGrade;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
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
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].grade").value("PSA10"))
                .andExpect(jsonPath("$[0].price").value(5000000))
                .andExpect(jsonPath("$[2].grade").value(nullValue()));
    }

    @Test
    void 체결_이력이_없으면_200과_빈_목록을_반환한다() throws Exception {
        given(priceService.getRecentTrades(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/prices/1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 존재하지_않는_카드면_404를_반환한다() throws Exception {
        given(priceService.getRecentTrades(999L))
                .willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));

        mockMvc.perform(get("/api/prices/999/trades"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }
}
