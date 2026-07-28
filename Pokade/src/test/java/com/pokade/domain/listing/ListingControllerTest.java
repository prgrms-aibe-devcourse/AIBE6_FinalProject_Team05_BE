package com.pokade.domain.listing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import com.pokade.domain.listing.dto.ListingSummaryResponse;
import com.pokade.domain.listing.dto.ListingUpdateRequest;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ListingService listingService;

    @Test
    void 매물_등록에_성공하면_201과_등록된_매물을_반환한다() throws Exception {
        ListingCreateRequest request = new ListingCreateRequest(
                1L, null, 10000, ListingGrade.A, List.of("https://example.com/a.png"));
        ListingResponse response = new ListingResponse(
                1L, 1L, 100L, null, 10000, ListingGrade.A, ListingStatus.ACTIVE,
                List.of("https://example.com/a.png"), LocalDateTime.now());

        given(listingService.createListing(anyLong(), any(ListingCreateRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/listings")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.sellerId").value(100L));
    }

    @Test
    void 필수값이_없으면_400을_반환한다() throws Exception {
        ListingCreateRequest invalidRequest = new ListingCreateRequest(null, null, null, null, List.of());

        mockMvc.perform(post("/api/listings")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 중복_등록이면_409를_반환한다() throws Exception {
        ListingCreateRequest request = new ListingCreateRequest(
                1L, null, 10000, ListingGrade.A, List.of("https://example.com/a.png"));

        given(listingService.createListing(anyLong(), any(ListingCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_LISTING));

        mockMvc.perform(post("/api/listings")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LISTING"));
    }

    @Test
    void 활성_매물이_있으면_200과_가격순_목록을_반환한다() throws Exception {
        ListingSummaryResponse summary = new ListingSummaryResponse(
                1L, 100L, 10000, ListingGrade.A, ListingStatus.ACTIVE,
                "https://example.com/a.png", LocalDateTime.now());

        given(listingService.getActiveListings(1L)).willReturn(List.of(summary));

        mockMvc.perform(get("/api/listings").param("cardId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void 활성_매물이_없으면_200과_빈_목록을_반환한다() throws Exception {
        given(listingService.getActiveListings(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/listings").param("cardId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cardId가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 내_매물이_있으면_200과_목록을_반환한다() throws Exception {
        ListingSummaryResponse summary = new ListingSummaryResponse(
                1L, 100L, 10000, ListingGrade.A, ListingStatus.ACTIVE,
                "https://example.com/a.png", LocalDateTime.now());

        given(listingService.getMyListings(100L, null)).willReturn(List.of(summary));

        mockMvc.perform(get("/api/listings/me").header("X-USER-ID", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void 등록한_매물이_없으면_200과_빈_목록을_반환한다() throws Exception {
        given(listingService.getMyListings(100L, null)).willReturn(List.of());

        mockMvc.perform(get("/api/listings/me").header("X-USER-ID", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void status_파라미터로_필터링해서_조회한다() throws Exception {
        ListingSummaryResponse summary = new ListingSummaryResponse(
                2L, 100L, 5000, ListingGrade.B, ListingStatus.SOLD,
                null, LocalDateTime.now());

        given(listingService.getMyListings(100L, ListingStatus.SOLD)).willReturn(List.of(summary));

        mockMvc.perform(get("/api/listings/me")
                        .header("X-USER-ID", 100L)
                        .param("status", "SOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SOLD"));
    }

    @Test
    void 매물_수정에_성공하면_200과_수정된_매물을_반환한다() throws Exception {
        ListingUpdateRequest request = new ListingUpdateRequest(20000);
        ListingResponse response = new ListingResponse(
                1L, 1L, 100L, null, 20000, ListingGrade.A, ListingStatus.ACTIVE,
                List.of("https://example.com/a.png"), LocalDateTime.now());

        given(listingService.updatePrice(anyLong(), anyLong(), any(ListingUpdateRequest.class)))
                .willReturn(response);

        mockMvc.perform(put("/api/listings/1")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(20000));
    }

    @Test
    void 수정시_가격이_없으면_400을_반환한다() throws Exception {
        ListingUpdateRequest invalidRequest = new ListingUpdateRequest(null);

        mockMvc.perform(put("/api/listings/1")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void ACTIVE_상태가_아니면_수정시_400을_반환한다() throws Exception {
        ListingUpdateRequest request = new ListingUpdateRequest(20000);

        given(listingService.updatePrice(anyLong(), anyLong(), any(ListingUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_LISTING_STATUS));

        mockMvc.perform(put("/api/listings/1")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LISTING_STATUS"));
    }

    @Test
    void 본인_매물이_아니면_수정시_403을_반환한다() throws Exception {
        ListingUpdateRequest request = new ListingUpdateRequest(20000);

        given(listingService.updatePrice(anyLong(), anyLong(), any(ListingUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(put("/api/listings/1")
                        .header("X-USER-ID", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 존재하지_않는_매물_수정시_404를_반환한다() throws Exception {
        ListingUpdateRequest request = new ListingUpdateRequest(20000);

        given(listingService.updatePrice(anyLong(), anyLong(), any(ListingUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        mockMvc.perform(put("/api/listings/999")
                        .header("X-USER-ID", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    void 매물_삭제에_성공하면_204를_반환한다() throws Exception {
        willDoNothing().given(listingService).deleteListing(anyLong(), anyLong());

        mockMvc.perform(delete("/api/listings/1").header("X-USER-ID", 100L))
                .andExpect(status().isNoContent());
    }

    @Test
    void ACTIVE_상태가_아니면_삭제시_400을_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.INVALID_LISTING_STATUS))
                .given(listingService).deleteListing(anyLong(), anyLong());

        mockMvc.perform(delete("/api/listings/1").header("X-USER-ID", 100L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LISTING_STATUS"));
    }

    @Test
    void 본인_매물이_아니면_삭제시_403을_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                .given(listingService).deleteListing(anyLong(), anyLong());

        mockMvc.perform(delete("/api/listings/1").header("X-USER-ID", 999L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 존재하지_않는_매물_삭제시_404를_반환한다() throws Exception {
        willThrow(new BusinessException(ErrorCode.LISTING_NOT_FOUND))
                .given(listingService).deleteListing(anyLong(), anyLong());

        mockMvc.perform(delete("/api/listings/999").header("X-USER-ID", 100L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }
}
