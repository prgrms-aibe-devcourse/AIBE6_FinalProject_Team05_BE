package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.dto.CardResponse;

/**
 * CardService는 CardQueryService/CardFacetService로 위임만 하는 파사드다(카드 도메인
 * 리팩토링 - 책임 분리). 여기서는 각 메서드가 정확히 어느 내부 서비스로, 어떤 인자로
 * 위임되는지만 검증한다 - 실제 검색/집계 로직 검증은 CardQueryServiceTest/CardFacetServiceTest에 있다.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardQueryService cardQueryService;

    @Mock
    private CardFacetService cardFacetService;

    @InjectMocks
    private CardService cardService;

    @Test
    @DisplayName("search(8-인자, languages 포함)는 CardQueryService.search(8-인자)로 위임한다")
    void searchWithLanguagesDelegatesToQueryService() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardQueryService.search(null, null, List.of("EN", "JA"), null, null, null, null, pageable))
                .willReturn(page);

        Page<CardResponse> result = cardService.search(null, null, List.of("EN", "JA"), null, null, null, null, pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("getDetail은 CardQueryService.getDetail로 위임한다")
    void getDetailDelegatesToQueryService() {
        CardDetailResponse detail = new CardDetailResponse(
                1L, "base1-4", "Charizard", null, "EN", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), null, null, null, null, null, null, List.of());
        given(cardQueryService.getDetail(1L)).willReturn(detail);

        CardDetailResponse result = cardService.getDetail(1L);

        assertThat(result).isSameAs(detail);
    }

    @Test
    @DisplayName("searchByKeyword는 CardQueryService.searchByKeyword로 위임한다")
    void searchByKeywordDelegatesToQueryService() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CardResponse> page = new PageImpl<>(List.of(), pageable, 0);
        given(cardQueryService.searchByKeyword("char", pageable)).willReturn(page);

        Page<CardResponse> result = cardService.searchByKeyword("char", pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("getRelated는 CardQueryService.getRelated로 위임한다")
    void getRelatedDelegatesToQueryService() {
        given(cardQueryService.getRelated(1L)).willReturn(List.of());

        List<CardResponse> result = cardService.getRelated(1L);

        assertThat(result).isEmpty();
        verify(cardQueryService).getRelated(1L);
    }

    @Test
    @DisplayName("getFacets는 CardFacetService.getFacets로 위임한다(CardQueryService는 호출하지 않는다)")
    void getFacetsDelegatesToFacetService() {
        CardFacetsResponse facets = CardFacetsResponse.of(List.of(), List.of(), List.of());
        given(cardFacetService.getFacets()).willReturn(facets);

        CardFacetsResponse result = cardService.getFacets();

        assertThat(result).isSameAs(facets);
        verifyNoQueryServiceInteraction();
    }

    private void verifyNoQueryServiceInteraction() {
        org.mockito.Mockito.verifyNoInteractions(cardQueryService);
    }
}
