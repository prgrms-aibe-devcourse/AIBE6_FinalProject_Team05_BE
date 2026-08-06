package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.web.PageableValidator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardService {

    /** CardController가 @PageableDefault에 쓰는 기본 페이지 크기. 원본 값은 {@link CardRepository#DEFAULT_PAGE_SIZE}. */
    public static final int DEFAULT_PAGE_SIZE = CardRepository.DEFAULT_PAGE_SIZE;
    // size 상한: 응답 payload/DB 부하를 고려해 BE 기본값(DEFAULT_PAGE_SIZE)의 5배 수준으로 제한.
    // application.yaml의 Pageable 전역 max-page-size(기본 2000)와 별개로 카드 도메인에서 한 번 더 검증.
    private static final int MAX_PAGE_SIZE = 100;
    // types/rarity 상한: 현재 FE 필터 옵션(각 6개)보다 넉넉히 여유를 둔 값.
    private static final int MAX_FILTER_VALUES = 20;
    // 키워드 검색어 상한: cards.name 컬럼 길이(200자)보다 짧게 잡아 과도하게 긴 ILIKE 패턴을 차단.
    private static final int MAX_KEYWORD_LENGTH = 100;
    // 카드 목록/상세 응답에 표시할 등급 값. PSA10/9/8은 감정 등급이라 표시 대상이 아니다.
    // 네이티브 쿼리 IN (:validGrades) 바인드 파라미터로 전달하기 위한 리스트 형태.
    private static final List<String> GRADE_WHITELIST_LIST = List.of("S", "A", "B");
    // 응답에 노출하는 등급 표시 순서. 화이트리스트와 동일한 범위로 제한한다.
    private static final List<String> GRADE_DISPLAY_ORDER = List.of("S", "A", "B");

    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;

    @Transactional(readOnly = true)
    public Page<CardResponse> search(List<String> types, List<String> rarities, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        validateFilterSize(types, "types");
        validateFilterSize(rarities, "rarity");
        validatePriceRange(minPrice, maxPrice);
        Page<Card> cards = cardRepository.search(types, rarities, expansionId, minPrice, maxPrice, sort, pageable);
        Map<Long, List<String>> gradesByCardId = fetchGradesByCardIds(cards.getContent());
        return cards.map(card -> CardResponse.from(card, gradesByCardId.getOrDefault(card.getId(), List.of())));
    }

    @Transactional
    public CardDetailResponse getDetail(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        cardRepository.incrementViewCount(id);
        List<CardVariant> variants = cardVariantRepository.findByCardIdOrderByPrimaryDescVariantNameAsc(id);
        Map<Long, List<String>> gradesByVariantId = groupByKey(cardVariantRepository.findGradesByCardId(id, GRADE_WHITELIST_LIST),
                CardVariantRepository.VariantGradeView::getVariantId, CardVariantRepository.VariantGradeView::getGrade);
        return CardDetailResponse.of(card, variants, gradesByVariantId);
    }

    private <T, K> Map<K, List<String>> groupByKey(List<T> views, Function<T, K> keyFn, Function<T, String> gradeFn) {
        Map<K, Set<String>> grouped = new HashMap<>();
        for (T view : views) {
            grouped.computeIfAbsent(keyFn.apply(view), k -> new HashSet<>()).add(gradeFn.apply(view));
        }
        Map<K, List<String>> result = new HashMap<>();
        grouped.forEach((key, gradeSet) -> result.put(key, GRADE_DISPLAY_ORDER.stream().filter(gradeSet::contains).toList()));
        return result;
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> searchByKeyword(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        String keyword = q.trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "검색어는 최대 " + MAX_KEYWORD_LENGTH + "자까지 입력할 수 있습니다.");
        }
        Page<Card> cards = cardRepository.findByNameContainingIgnoreCase(keyword, pageable);
        Map<Long, List<String>> gradesByCardId = fetchGradesByCardIds(cards.getContent());
        return cards.map(card -> CardResponse.from(card, gradesByCardId.getOrDefault(card.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getRelated(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        List<Card> related;
        if (hasPokedexNumber(card)) {
            related = cardRepository.findRelatedByPokedexNumber(id);
        } else if (card.getExpansion() != null) {
            related = cardRepository.findRelatedByExpansion(card.getExpansion().getId(), id);
        } else {
            related = List.of();
        }
        Map<Long, List<String>> gradesByCardId = fetchGradesByCardIds(related);
        return related.stream()
                .map(c -> CardResponse.from(c, gradesByCardId.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<String>> fetchGradesByCardIds(List<Card> cards) {
        if (cards.isEmpty()) {
            return Map.of();
        }
        List<Long> cardIds = cards.stream().map(Card::getId).toList();
        return groupByKey(cardRepository.findGradesByCardIds(cardIds, GRADE_WHITELIST_LIST),
                CardRepository.CardGradeView::getCardId, CardRepository.CardGradeView::getGrade);
    }

    /**
     * Scrydex external_id로 내부 카드 엔티티를 조회한다. AI 등급진단 등 다른 도메인이
     * vision/외부 식별 결과를 내부 card_id에 매핑할 때 사용할 수 있도록 제공하는 조회 전용 메서드.
     */
    @Transactional(readOnly = true)
    public Optional<Card> findByExternalId(String externalId) {
        return cardRepository.findByExternalId(externalId);
    }

    private boolean hasPokedexNumber(Card card) {
        return card.getNationalPokedexNumbers() != null && !card.getNationalPokedexNumbers().isEmpty();
    }

    private void validateFilterSize(List<String> values, String fieldName) {
        if (values != null && values.size() > MAX_FILTER_VALUES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    fieldName + "는 최대 " + MAX_FILTER_VALUES + "개까지 지정할 수 있습니다.");
        }
    }

    private void validatePriceRange(Integer minPrice, Integer maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "minPrice는 maxPrice보다 클 수 없습니다.");
        }
    }
}