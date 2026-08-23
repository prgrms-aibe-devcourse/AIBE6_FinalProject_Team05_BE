package com.pokade.domain.watchlist.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.auth.service.OAuth2LoginService;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.watchlist.dto.WatchlistCountResponse;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.dto.WatchlistUpdateRequest;
import com.pokade.domain.watchlist.service.WatchlistService;
import com.pokade.global.config.SecurityConfig;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtAuthenticationEntryPoint;
import com.pokade.global.security.JwtTokenProvider;
import com.pokade.global.security.TokenBlacklistStore;
import com.pokade.global.security.oauth.CustomOAuth2UserService;
import com.pokade.global.security.oauth.OAuth2LoginFailureHandler;
import com.pokade.global.security.oauth.OAuth2LoginSuccessHandler;
import com.pokade.global.security.oauth.RedisAuthorizationRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @MockitoBean
    private OAuth2LoginService oAuth2LoginService;

    @MockitoBean
    private RedisAuthorizationRequestRepository redisAuthorizationRequestRepository;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    private RequestPostProcessor userId(Long userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(auth);
    }

    @Test
    void 등록에_성공하면_200과_등록된_항목을_반환한다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 10000, null);
        WatchlistResponse response = new WatchlistResponse(
                1L, 1L, null, "피카츄", null, "기본팩", "image.png", 10000, null, false, LocalDateTime.now(), null, null, false);

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
    void 등록시_목표가가_상한_1억원을_넘으면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 100_000_001, null);

        mockMvc.perform(post("/api/watchlist")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.msg").value("targetBuyPrice: 목표가는 1억원을 초과할 수 없습니다."));

        then(watchlistService).should(never()).addWatchlist(anyLong(), any(WatchlistCreateRequest.class));
    }

    @Test
    void 등록시_목표가가_상한_1억원_정확히면_통과한다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 100_000_000, null);

        given(watchlistService.addWatchlist(anyLong(), any(WatchlistCreateRequest.class)))
                .willReturn(new WatchlistResponse(
                        1L, 1L, null, "피카츄", null, "기본팩", "image.png", 100_000_000, null, false, LocalDateTime.now(), null, null, false));

        mockMvc.perform(post("/api/watchlist")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void 수정시_목표가가_상한_1억원을_넘으면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
        WatchlistUpdateRequest request = new WatchlistUpdateRequest(null, 100_000_001, null);

        mockMvc.perform(patch("/api/watchlist/1")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.msg").value("targetSellPrice: 목표가는 1억원을 초과할 수 없습니다."));

        then(watchlistService).should(never()).updateWatchlist(anyLong(), anyLong(), any(WatchlistUpdateRequest.class));
    }

    @Test
    void 목표가_역전_조합이면_400과_INVALID_TARGET_PRICE_RANGE를_반환한다() throws Exception {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 5000, 3000);

        given(watchlistService.addWatchlist(anyLong(), any(WatchlistCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_TARGET_PRICE_RANGE));

        mockMvc.perform(post("/api/watchlist")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET_PRICE_RANGE"));
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
                1L, 1L, null, "피카츄", null, "기본팩", "image.png", 10000, null, false, LocalDateTime.now(), null, null, false);

        given(watchlistService.getWatchlist(100L)).willReturn(List.of(response));

        mockMvc.perform(get("/api/watchlist").with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1L));
    }

    @Test
    void 목록_조회_응답에_현재_시세와_목표가_도달_여부가_포함된다() throws Exception {
        CardPriceSummaryResponse currentPrice =
                new CardPriceSummaryResponse(1L, 9000, 8000, null, "KRW", null, null);
        WatchlistResponse response = new WatchlistResponse(
                1L, 1L, null, "피카츄", "피카츄", "기본팩", "image.png", 10000, null, false, LocalDateTime.now(),
                currentPrice, new java.math.BigDecimal("3.25"), true);

        given(watchlistService.getWatchlist(100L)).willReturn(List.of(response));

        mockMvc.perform(get("/api/watchlist").with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cardName").value("피카츄"))
                .andExpect(jsonPath("$.data[0].cardNameKo").value("피카츄"))
                .andExpect(jsonPath("$.data[0].currentPrice.buyPrice").value(9000))
                .andExpect(jsonPath("$.data[0].currentPrice.sellPrice").value(8000))
                .andExpect(jsonPath("$.data[0].changeRate").value(3.25))
                .andExpect(jsonPath("$.data[0].targetReached").value(true));
    }

    @Test
    void 관심수_조회에_성공하면_200과_카드별_등록수를_반환한다() throws Exception {
        given(watchlistService.getWatchlistCounts(List.of(1L, 2L)))
                .willReturn(List.of(new WatchlistCountResponse(1L, 3L), new WatchlistCountResponse(2L, 0L)));

        mockMvc.perform(get("/api/watchlist/counts")
                        .param("cardIds", "1,2")
                        .with(userId(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cardId").value(1L))
                .andExpect(jsonPath("$.data[0].count").value(3))
                .andExpect(jsonPath("$.data[1].cardId").value(2L))
                .andExpect(jsonPath("$.data[1].count").value(0));
    }

    @Test
    void 관심수_조회시_cardIds가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/watchlist/counts").with(userId(100L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 관심수_조회시_상한을_초과하면_400을_반환한다() throws Exception {
        // 소수(예: 2개)만 보내고 서비스가 예외를 던지도록 흉내내면 "쿼리 파라미터 파싱이 실제로 101개를
        // 만들어내는지"는 검증되지 않는다 - 실제 1~101 cardId를 콤마로 이어 보내고, 서비스에 전달된
        // 리스트를 캡처해 크기/내용까지 확인한다. 상한(100개) 초과 시 실제로 예외를 던지는 로직 자체는
        // WatchlistServiceTest.getWatchlistCounts_tooManyCardIds에서 이미 검증한다 - 여기서는 컨트롤러의
        // 파라미터 바인딩과 예외 -> 400 응답 변환만 확인한다.
        List<Long> expectedCardIds = LongStream.rangeClosed(1, 101).boxed().toList();
        String cardIdsParam = expectedCardIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        given(watchlistService.getWatchlistCounts(any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "cardIds는 최대 100개까지 지정할 수 있습니다."));

        mockMvc.perform(get("/api/watchlist/counts")
                        .param("cardIds", cardIdsParam)
                        .with(userId(100L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        ArgumentCaptor<List<Long>> cardIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(watchlistService).getWatchlistCounts(cardIdsCaptor.capture());
        assertThat(cardIdsCaptor.getValue()).hasSize(101).containsExactlyElementsOf(expectedCardIds);
    }

    @Test
    void 목표가_수정에_성공하면_200과_수정된_항목을_반환한다() throws Exception {
        WatchlistUpdateRequest request = new WatchlistUpdateRequest(20000, null, null);
        WatchlistResponse response = new WatchlistResponse(
                1L, 1L, null, null, null, null, null, 20000, null, false, LocalDateTime.now(), null, null, false);

        given(watchlistService.updateWatchlist(anyLong(), anyLong(), any(WatchlistUpdateRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/watchlist/1")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.targetBuyPrice").value(20000));
    }

    @Test
    void 목표가_수정_요청의_resendNotification_필드가_실제로_역직렬화된다() throws Exception {
        WatchlistResponse response = new WatchlistResponse(
                1L, 1L, null, null, null, null, null, 1000, null, false, LocalDateTime.now(), null, null, false);

        given(watchlistService.updateWatchlist(anyLong(), anyLong(), any(WatchlistUpdateRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/watchlist/1")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resendNotification\":true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WatchlistUpdateRequest> requestCaptor = ArgumentCaptor.forClass(WatchlistUpdateRequest.class);
        verify(watchlistService).updateWatchlist(anyLong(), anyLong(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().resendNotification()).isTrue();
        assertThat(requestCaptor.getValue().targetBuyPrice()).isNull();
    }

    @Test
    void 목표가_수정시_둘_다_없으면_400을_반환한다() throws Exception {
        WatchlistUpdateRequest request = new WatchlistUpdateRequest(null, null, null);

        given(watchlistService.updateWatchlist(anyLong(), anyLong(), any(WatchlistUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.TARGET_PRICE_REQUIRED));

        mockMvc.perform(patch("/api/watchlist/1")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_PRICE_REQUIRED"));
    }

    @Test
    void 존재하지_않는_항목_수정시_404를_반환한다() throws Exception {
        WatchlistUpdateRequest request = new WatchlistUpdateRequest(20000, null, null);

        given(watchlistService.updateWatchlist(anyLong(), anyLong(), any(WatchlistUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        mockMvc.perform(patch("/api/watchlist/999")
                        .with(userId(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));
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
