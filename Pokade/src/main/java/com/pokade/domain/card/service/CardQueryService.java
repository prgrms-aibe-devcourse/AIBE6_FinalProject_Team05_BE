package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.PokedexKoNameRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.card.support.CardRarityResolver;
import com.pokade.domain.card.support.CardTypeEnResolver;
import com.pokade.domain.card.support.KoreanTextUtil;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.web.PageableValidator;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 카드 검색/상세/유사 카드 조회 등 "조회" 책임만 담당한다. 필터 옵션 집계(카드 수 카운트)는
 * CardFacetService로 분리돼 있다. CardService(파사드)가 이 클래스와 CardFacetService를
 * 감싸 다른 도메인(예: domain.chat.tool.PriceChatTools)이 기존처럼 CardService 하나만
 * 의존해도 되도록 유지한다.
 */
@Service
@RequiredArgsConstructor
public class CardQueryService {

    // size 상한: 응답 payload/DB 부하를 고려해 BE 기본값(CardService.DEFAULT_PAGE_SIZE)의 5배 수준으로 제한.
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
    private final PokedexKoNameRepository pokedexKoNameRepository;
    private final CardNameKoResolver cardNameKoResolver;

    // Actuator/Prometheus 로컬 실험용 계측 - 커밋 대상 아님.
    // final이 아니라 Lombok @RequiredArgsConstructor 생성 대상에서 빠져 기존 테스트(@InjectMocks) 영향 없음.
    // required = false: @DataJpaTest 등 슬라이스 테스트엔 MeterRegistry 빈이 없어 NoSuchBeanDefinitionException으로
    // 컨텍스트 로딩 자체가 깨졌다(#224). 매칭되는 빈이 없으면 Spring이 필드를 건드리지 않고 그대로 두므로
    // (value == null이면 field.set() 자체를 안 함), 아래 기본값(SimpleMeterRegistry)이 계속 살아남아 null이 되지 않는다.
    @Autowired(required = false)
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    /**
     * languages 없이 호출하는 기존 오버로드 - #263 이전부터 있던 호출부(테스트 다수 포함)가
     * 인자 개수 때문에 깨지지 않도록 유지한다. 실제 검색은 8-인자 오버로드로 위임한다.
     */
    public Page<CardResponse> search(List<String> types, List<String> rarities, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        return search(types, rarities, null, expansionId, minPrice, maxPrice, sort, pageable);
    }

    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    // #263: language(언어 코드, 예 EN/JA) 필터 추가 - types/rarity와 동일하게 값 화이트리스트 없이
    // 사이즈 검증만 한다(바인드 IN절이라 애초에 인젝션 여지가 없고, DB에 실제 존재하는 값과 무관하게
    // 빈 결과로 안전하게 좁혀지므로 신규 언어코드가 추가돼도 서비스가 깨지지 않는다).
    @Timed(value = "card.search.duration")
    @Transactional(readOnly = true)
    public Page<CardResponse> search(List<String> types, List<String> rarities, List<String> languages, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        validateFilterSize(types, "types");
        validateFilterSize(rarities, "rarity");
        validateFilterSize(languages, "languages");
        validatePriceRange(minPrice, maxPrice);
        List<String> expandedTypes = CardTypeEnResolver.resolveOriginalValues(types);
        List<String> expandedRarities = CardRarityResolver.resolveOriginalValues(rarities);
        // languages가 없으면 리포지토리의 기존 7-인자 search()를 그대로 호출한다 - #263 이전부터 있던
        // CardServiceTest의 cardRepository.search(...) 스텁(7-인자 시그니처)이 계속 매칭되게 하기 위함.
        // 두 오버로드는 리포지토리 쪽에서 동일한 로직으로 수렴하므로(7-인자는 8-인자에 languages=null로
        // 위임) 동작 자체는 완전히 같다 - 순전히 테스트 호환을 위한 분기다.
        Page<Card> cards = languages == null
                ? cardRepository.search(expandedTypes, expandedRarities, expansionId, minPrice, maxPrice, sort, pageable)
                : cardRepository.search(expandedTypes, expandedRarities, languages, expansionId, minPrice, maxPrice, sort, pageable);
        Map<Long, List<String>> gradesByCardId = fetchGradesByCardIds(cards.getContent());
        return cards.map(card -> toCardResponse(card, gradesByCardId));
    }

    @Transactional
    public CardDetailResponse getDetail(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        cardRepository.incrementViewCount(id);
        // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
        meterRegistry.counter("card.view.increment.calls").increment();
        List<CardVariant> variants = cardVariantRepository.findByCardIdOrderByPrimaryDescVariantNameAsc(id);
        Map<Long, List<String>> gradesByVariantId = groupByKey(cardVariantRepository.findGradesByCardId(id, GRADE_WHITELIST_LIST),
                CardVariantRepository.VariantGradeView::getVariantId, CardVariantRepository.VariantGradeView::getGrade);
        return CardDetailResponse.of(card, variants, gradesByVariantId, cardNameKoResolver.resolve(card), CardTypeEnResolver.resolve(card.getTypes()), CardRarityResolver.resolve(card.getRarityCode(), card.getRarity()));
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

    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    @Timed(value = "card.search.keyword.duration")
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
        Page<Card> cards = (KoreanTextUtil.isKorean(keyword) || KoreanTextUtil.isChosungOnly(keyword))
                ? searchByPokedexKoName(keyword, pageable)
                : cardRepository.findByNameContainingIgnoreCase(keyword, pageable);
        Map<Long, List<String>> gradesByCardId = fetchGradesByCardIds(cards.getContent());
        return cards.map(card -> toCardResponse(card, gradesByCardId));
    }

    // 한글 검색어를 도감번호 목록으로 변환해 조회한다. 매핑이 없으면 예외 대신 빈 페이지를 반환한다.
    // 검색어가 자음(초성)으로만 이뤄져 있으면 초성 검색, 아니면 이름 부분일치로 검색한다.
    // 한글/초성 검색은 도감번호(national_pokedex_numbers) 매핑 기반이라 포켓몬 카드만 지원한다.
    // 도감번호가 없는 트레이너/에너지 카드는 이 경로로 검색되지 않는다 - 의도된 한계
    // (PokeAPI 도감번호 매핑 방식의 알려진 제약).
    private Page<Card> searchByPokedexKoName(String keyword, Pageable pageable) {
        List<PokedexKoName> matches = KoreanTextUtil.isChosungOnly(keyword)
                ? pokedexKoNameRepository.findByNameKoChosungContaining(keyword)
                : pokedexKoNameRepository.findByNameKoContaining(keyword);
        if (matches.isEmpty()) {
            return Page.empty(pageable);
        }
        List<Integer> pokedexNumbers = matches.stream().map(PokedexKoName::getPokedexNumber).toList();
        return cardRepository.findByNationalPokedexNumbersIn(pokedexNumbers, pageable);
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
                .map(relatedCard -> toCardResponse(relatedCard, gradesByCardId))
                .toList();
    }

    private CardResponse toCardResponse(Card card, Map<Long, List<String>> gradesByCardId) {
        return CardResponse.from(card, gradesByCardId.getOrDefault(card.getId(), List.of()),
                cardNameKoResolver.resolve(card), CardTypeEnResolver.resolve(card.getTypes()),
                CardRarityResolver.resolve(card.getRarityCode(), card.getRarity()));
    }

    private Map<Long, List<String>> fetchGradesByCardIds(List<Card> cards) {
        if (cards.isEmpty()) {
            return Map.of();
        }
        // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
        meterRegistry.counter("card.grade.batch.calls").increment();
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
