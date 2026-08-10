package com.pokade.domain.watchlist.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.service.WatchlistService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private WatchlistService watchlistService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    private RequestPostProcessor userId(Long userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    @Test
    void 등록에_성공하면_200과_등록된_항목을_반환한다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 10000, null);
        WatchlistResponse response = new WatchlistResponse(
                1L, 1L, null, 10000, null, false, LocalDateTime.now());

        given(watchlistService.addWatchlist(anyLong(), any(WatchlistCreateRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/watchlist")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.cardId").value(1L))
                .andExpect(jsonPath("$.data.targetBuyPrice").value(10000));
    }

    @Test
    void 목표가가_둘_다_없으면_400을_반환한다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, null, null);

        given(watchlistService.addWatchlist(anyLong(), any(WatchlistCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.TARGET_PRICE_REQUIRED));

        mockMvc.perform(post("/api/watchlist")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_PRICE_REQUIRED"));
    }

    @Test
    void 이미_등록된_카드면_409를_반환한다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 10000, null);

        given(watchlistService.addWatchlist(anyLong(), any(WatchlistCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_WATCHLIST));

        mockMvc.perform(post("/api/watchlist")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_WATCHLIST"));
    }

    @Test
    void 목록_조회에_성공하면_200과_목록을_반환한다() throws Exception {
        WatchlistResponse response = new WatchlistResponse(
                1L, 1L, null, 10000, null, false, LocalDateTime.now());

        given(watchlistService.getWatchlist(100L)).willReturn(List.of(response));

        mockMvc.perform(get("/api/watchlist").with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    void 삭제에_성공하면_200을_반환한다() throws Exception {
        willDoNothing().given(watchlistService).deleteWatchlist(anyLong(), anyLong());

        mockMvc.perform(delete("/api/watchlist/1").with(userId(100L)))
                .andExpect(status().isOk());
    }

    @Test
    void 존재하지_않는_항목_삭제시_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND))
                .given(watchlistService).deleteWatchlist(anyLong(), anyLong());

        mockMvc.perform(delete("/api/watchlist/999").with(userId(100L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));
    }
}
