package com.pokade.domain.trade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.trade.dto.TradeCreateRequest;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TradeService tradeService;

    @Test
    void 즉시구매에_성공하면_201과_생성된_거래를_반환한다() throws Exception {
        TradeCreateRequest request = new TradeCreateRequest(1L);
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 10000, TradeStatus.PENDING,
                null, null, null, LocalDateTime.now());

        given(tradeService.createTrade(anyLong(), any(TradeCreateRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/trades")
                        .header("X-USER-ID", 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.buyerId").value(200L))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listingId가_없으면_400을_반환한다() throws Exception {
        TradeCreateRequest invalidRequest = new TradeCreateRequest(null);

        mockMvc.perform(post("/api/trades")
                        .header("X-USER-ID", 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 본인_매물을_구매하려하면_400을_반환한다() throws Exception {
        TradeCreateRequest request = new TradeCreateRequest(1L);

        given(tradeService.createTrade(anyLong(), any(TradeCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.SELF_PURCHASE_NOT_ALLOWED));

        mockMvc.perform(post("/api/trades")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PURCHASE_NOT_ALLOWED"));
    }

    @Test
    void 존재하지_않는_매물이면_404를_반환한다() throws Exception {
        TradeCreateRequest request = new TradeCreateRequest(999L);

        given(tradeService.createTrade(anyLong(), any(TradeCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        mockMvc.perform(post("/api/trades")
                        .header("X-USER-ID", 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    void 이미_거래중인_매물이면_409를_반환한다() throws Exception {
        TradeCreateRequest request = new TradeCreateRequest(1L);

        given(tradeService.createTrade(anyLong(), any(TradeCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.TRADE_CONFLICT));

        mockMvc.perform(post("/api/trades")
                        .header("X-USER-ID", 200L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_CONFLICT"));
    }

    @Test
    void 본인_거래를_조회하면_200과_거래정보를_반환한다() throws Exception {
        TradeResponse response = new TradeResponse(
                1L, 1L, 200L, 10000, TradeStatus.PENDING,
                null, null, null, LocalDateTime.now());

        given(tradeService.getTrade(200L, 1L)).willReturn(response);

        mockMvc.perform(get("/api/trades/{id}", 1L)
                        .header("X-USER-ID", 200L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.buyerId").value(200L))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 본인_거래가_아니면_403을_반환한다() throws Exception {
        given(tradeService.getTrade(999L, 1L))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(get("/api/trades/{id}", 1L)
                        .header("X-USER-ID", 999L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 존재하지_않는_거래를_조회하면_404를_반환한다() throws Exception {
        given(tradeService.getTrade(200L, 999L))
                .willThrow(new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        mockMvc.perform(get("/api/trades/{id}", 999L)
                        .header("X-USER-ID", 200L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }
}
