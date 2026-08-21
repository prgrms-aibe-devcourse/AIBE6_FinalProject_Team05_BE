package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.repository.CardRepository;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * 카드 도메인의 외부 진입점 파사드. 실제 로직은 CardQueryService(검색/상세/related/외부ID 조회)와
 * CardFacetService(필터 옵션 집계)로 분리돼 있고, 이 클래스는 위임만 한다.
 *
 * 분리하지 않고 이 클래스를 그대로 유지하는 이유: CardController뿐 아니라 다른 도메인
 * (예: domain.chat.tool.PriceChatTools)이 이미 "CardService"라는 타입으로 직접 의존하고 있어,
 * 그 계약(클래스명·public 메서드 시그니처)을 그대로 유지해야 해당 파일들을 건드리지 않고도
 * 내부 구조만 재정리할 수 있다. 위임 메서드는 각각 CardQueryService/CardFacetService의 실제 빈을
 * 호출하므로(같은 빈 내부의 self-invocation이 아니므로), 이동한 @Timed/@Transactional/@Cacheable은
 * 원래 메서드에서 그대로 동작한다.
 */
@Service
@RequiredArgsConstructor
public class CardService {

    /** CardController가 @PageableDefault에 쓰는 기본 페이지 크기. 원본 값은 {@link CardRepository#DEFAULT_PAGE_SIZE}. */
    public static final int DEFAULT_PAGE_SIZE = CardRepository.DEFAULT_PAGE_SIZE;

    private final CardQueryService cardQueryService;
    private final CardFacetService cardFacetService;

    public Page<CardResponse> search(List<String> types, List<String> rarities, List<String> languages, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        return cardQueryService.search(types, rarities, languages, expansionId, minPrice, maxPrice, sort, pageable);
    }

    /** #308: q(키워드)가 추가된 오버로드 - GET /api/cards?q=... 필터+키워드 통합 검색이 이 메서드로 들어온다. */
    public Page<CardResponse> search(String q, List<String> types, List<String> rarities, List<String> languages, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        return cardQueryService.search(q, types, rarities, languages, expansionId, minPrice, maxPrice, sort, pageable);
    }

    public CardDetailResponse getDetail(Long id) {
        return cardQueryService.getDetail(id);
    }

    public Page<CardResponse> searchByKeyword(String q, Pageable pageable) {
        return cardQueryService.searchByKeyword(q, pageable);
    }

    public List<CardResponse> getRelated(Long id) {
        return cardQueryService.getRelated(id);
    }

    public CardFacetsResponse getFacets() {
        return cardFacetService.getFacets();
    }
}
