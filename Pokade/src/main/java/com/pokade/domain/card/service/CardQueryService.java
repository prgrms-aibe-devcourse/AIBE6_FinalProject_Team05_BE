package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    // #187: pg_trgm similarity() 유사도 폴백 검색의 최소 점수 - 스파이크 테스트(리자옹→리자몽/리자드 0.33,
    // 핏카츄→피카츄 0.14, 꼬부리→꼬부기 0.33) 기준으로 시작. 실제 운영에서 노이즈가 많으면 올리는 방향으로 조정.
    private static final double SIMILARITY_THRESHOLD = 0.14;
    // #187: 이보다 짧으면 유사도 폴백을 생략한다 - 1글자는 트라이그램 자체가 구분력이 없다(스파이크에서
    // "리" 한 글자로는 후보 전체가 비슷한 점수로 몰려 무의미했음).
    private static final int MIN_KEYWORD_LENGTH_FOR_SIMILARITY = 2;
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
     * 인자 개수 때문에 깨지지 않도록 유지한다. 실제 검색은 9-인자(키워드 포함) 오버로드로 위임한다.
     */
    public Page<CardResponse> search(List<String> types, List<String> rarities, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        return search(types, rarities, null, expansionId, minPrice, maxPrice, sort, pageable);
    }

    /**
     * q(키워드) 없이 호출하는 기존 오버로드 - #308 이전부터 있던 호출부(테스트 다수 포함)가 인자
     * 개수 때문에 깨지지 않도록 유지한다. 실제 검색은 9-인자(키워드 포함) 오버로드로 위임하며,
     * q=null이면 그 메서드 안에서 기존과 동일하게 필터 전용 경로(cardRepository.search())를 탄다 -
     * 동작은 완전히 그대로다.
     */
    public Page<CardResponse> search(List<String> types, List<String> rarities, List<String> languages, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        return search(null, types, rarities, languages, expansionId, minPrice, maxPrice, sort, pageable);
    }

    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    // #263: language(언어 코드, 예 EN/JA) 필터 추가 - types/rarity와 동일하게 값 화이트리스트 없이
    // 사이즈 검증만 한다(바인드 IN절이라 애초에 인젝션 여지가 없고, DB에 실제 존재하는 값과 무관하게
    // 빈 결과로 안전하게 좁혀지므로 신규 언어코드가 추가돼도 서비스가 깨지지 않는다).
    // #308: q(키워드)가 추가된 실제 구현 메서드 - GET /api/cards?q=... 필터+키워드 통합 검색의 진입점.
    // q가 없으면(null/blank) 기존 필터 전용 로직을 그대로 타고(회귀 없음), q가 있으면 필터+키워드
    // 결합 검색(searchByKeywordAndFilters)으로 분기한다.
    @Timed(value = "card.search.duration")
    @Transactional(readOnly = true)
    public Page<CardResponse> search(String q, List<String> types, List<String> rarities, List<String> languages, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        validateFilterSize(types, "types");
        validateFilterSize(rarities, "rarity");
        validateFilterSize(languages, "languages");
        validatePriceRange(minPrice, maxPrice);
        String keyword = normalizeOptionalKeyword(q);
        List<String> expandedTypes = CardTypeEnResolver.resolveOriginalValues(types);
        List<String> expandedRarities = CardRarityResolver.resolveOriginalValues(rarities);

        NameSearchResult result = keyword == null
                // 키워드 없음: 기존 그대로 - languages가 없으면 리포지토리의 기존 7-인자 search()를
                // 그대로 호출한다 - #263 이전부터 있던 CardServiceTest의 cardRepository.search(...)
                // 스텁(7-인자 시그니처)이 계속 매칭되게 하기 위함(두 오버로드는 리포지토리 쪽에서
                // languages=null로 수렴해 동작은 동일).
                ? new NameSearchResult(languages == null
                        ? cardRepository.search(expandedTypes, expandedRarities, expansionId, minPrice, maxPrice, sort, pageable)
                        : cardRepository.search(expandedTypes, expandedRarities, languages, expansionId, minPrice, maxPrice, sort, pageable),
                        false)
                : searchByKeywordAndFilters(keyword, expandedTypes, expandedRarities, languages, expansionId, minPrice, maxPrice, sort, pageable);

        Map<Long, List<String>> gradesByCardId = fetchGradesByCardIds(result.cards().getContent());
        return result.cards().map(card -> toCardResponse(card, gradesByCardId, result.fuzzyMatch()));
    }

    /** q가 null/blank면 "키워드 없음"으로 취급해 null을 반환한다(예외 아님) - searchByKeyword()의 필수 검증과 다른 지점. */
    private String normalizeOptionalKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String keyword = q.trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "검색어는 최대 " + MAX_KEYWORD_LENGTH + "자까지 입력할 수 있습니다.");
        }
        return keyword;
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

    /**
     * #308: q(키워드)가 필수인 기존 전용 엔드포인트(GET /api/cards/search, PriceChatTools 하위 호환
     * 필수) - 시그니처를 절대 바꾸지 않고 실제 검색은 9-인자 search()로 위임한다. 필터 없이(모두 null)
     * 호출하고, sort는 SORT_NAME으로 고정한다 - 이 엔드포인트는 원래 sort 파라미터 자체가 없었고
     * 항상 "이름 오름차순"(영문 정확일치)/"도감번호 매칭 후 이름 오름차순"(한글)으로 동작했으므로,
     * 9-인자 쪽 기본값인 latest로 흘러가면 이 엔드포인트의 기본 정렬이 조용히 바뀌는 회귀가 된다.
     * @Timed/@Transactional을 이 메서드에도 그대로 유지하는 이유: self-invocation으로 search()를
     * 호출하면 그쪽 애노테이션은 AOP 프록시를 안 거쳐 적용되지 않으므로, "card.search.keyword.duration"
     * 지표와 트랜잭션 경계는 이 메서드 자신의 애노테이션이 책임진다(기존 7→8인자 오버로드 위임 패턴과
     * 동일하게, 위임만 하는 메서드는 호출 대상 메서드의 애노테이션에 의존하지 않는다).
     */
    @Timed(value = "card.search.keyword.duration")
    @Transactional(readOnly = true)
    public Page<CardResponse> searchByKeyword(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return search(q, null, null, null, null, null, null, CardRepository.SORT_NAME, pageable);
    }

    // searchByName()/searchByPokedexKoName()이 정확 검색과 유사도 폴백 중 어느 쪼을 탔는지(#187 fuzzyMatch
    // 응답 플래그)를 함께 반환하기 위한 내부 홀더. Page<Card>만으로는 그 정보가 사라진다.
    private record NameSearchResult(Page<Card> cards, boolean fuzzyMatch) {
    }

    // #308: search()의 필터 전처리(CardRepository.search() 기본 메서드가 하던 것과 동일한 규칙:
    // blank 제거 → hasX 판단 → IN절/overlap용 안전값 대체)를 키워드+필터 결합 쿼리 호출부에서도
    // 그대로 적용하기 위한 값 홀더. 필터 전용 경로(cardRepository.search())는 건드리지 않고 그대로
    // 둬서 이미 검증된 로직에 회귀 위험을 만들지 않는 대신, 이 부분은 의도적으로 그 로직을 다시 구현했다.
    private record ResolvedFilters(boolean hasTypes, String[] types, boolean hasRarities, List<String> rarities,
                                    boolean hasLanguages, List<String> languages, boolean hasPrice) {
    }

    private ResolvedFilters resolveFilters(List<String> types, List<String> rarities, List<String> languages, Integer minPrice, Integer maxPrice) {
        List<String> filteredTypes = types == null ? null : types.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredRarities = rarities == null ? null : rarities.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredLanguages = languages == null ? null : languages.stream().filter(v -> v != null && !v.isBlank()).toList();
        boolean hasTypes = filteredTypes != null && !filteredTypes.isEmpty();
        boolean hasRarities = filteredRarities != null && !filteredRarities.isEmpty();
        boolean hasLanguages = filteredLanguages != null && !filteredLanguages.isEmpty();
        boolean hasPrice = minPrice != null || maxPrice != null;
        String[] safeTypes = (hasTypes ? filteredTypes : List.<String>of()).toArray(String[]::new);
        List<String> safeRarities = hasRarities ? filteredRarities : List.of("");
        List<String> safeLanguages = hasLanguages ? filteredLanguages : List.of("");
        return new ResolvedFilters(hasTypes, safeTypes, hasRarities, safeRarities, hasLanguages, safeLanguages, hasPrice);
    }

    /** CardRepository.search()와 동일한 이유로 Pageable의 Sort를 버린다 - ?sort= 파라미터명 충돌 방지. */
    private Pageable stripSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private String resolveSort(String sort) {
        return sort != null && CardRepository.SORT_COLUMN_WHITELIST.contains(sort) ? sort : CardRepository.SORT_LATEST;
    }

    // #308: 한글/초성이면 도감번호 매핑 경로, 아니면 영문 ILIKE 경로로 분기한다 - 기존 searchByKeyword()의
    // 분기 조건을 그대로 옮겼다.
    private NameSearchResult searchByKeywordAndFilters(String keyword, List<String> types, List<String> rarities,
                                                         List<String> languages, String expansionId, Integer minPrice, Integer maxPrice,
                                                         String sort, Pageable pageable) {
        return (KoreanTextUtil.isKorean(keyword) || KoreanTextUtil.isChosungOnly(keyword))
                ? searchByPokedexKoName(keyword, types, rarities, languages, expansionId, minPrice, maxPrice, sort, pageable)
                : searchByName(keyword, types, rarities, languages, expansionId, minPrice, maxPrice, sort, pageable);
    }

    /**
     * #308: 영문 등 이름 검색 + 필터 결합. 폴백 판단 3단계(설계 승인안):
     * ① 필터+정확일치 1건 이상 → 그대로 반환.
     * ② 필터+정확일치 0건이지만 필터 없는 정확일치는 존재(existsByNameContainingIgnoreCase) →
     *    "필터가 세서 없는 것"으로 보고 빈 페이지 반환, 유사도 폴백을 태우지 않는다.
     * ③ 필터 없는 정확일치도 없음(키워드 자체가 애매함) → 기존처럼(#187) 유사도 폴백, 필터도 동일하게 적용.
     */
    private NameSearchResult searchByName(String keyword, List<String> types, List<String> rarities, List<String> languages,
                                           String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        ResolvedFilters f = resolveFilters(types, rarities, languages, minPrice, maxPrice);
        Pageable unsortedPageable = stripSort(pageable);
        Page<Card> filteredExact = dispatchNameExact(sort, keyword, f, expansionId, minPrice, maxPrice, unsortedPageable);
        if (filteredExact.getTotalElements() > 0 || keyword.length() < MIN_KEYWORD_LENGTH_FOR_SIMILARITY) {
            return new NameSearchResult(filteredExact, false);
        }
        if (cardRepository.existsByNameContainingIgnoreCase(keyword)) {
            return new NameSearchResult(Page.empty(pageable), false);
        }
        Page<Card> similar = cardRepository.searchByNameSimilarToWithFilters(keyword, SIMILARITY_THRESHOLD,
                f.hasTypes(), f.types(), f.hasRarities(), f.rarities(), f.hasLanguages(), f.languages(),
                f.hasPrice(), minPrice, maxPrice, expansionId, unsortedPageable);
        return new NameSearchResult(similar, similar.getTotalElements() > 0);
    }

    private Page<Card> dispatchNameExact(String sort, String keyword, ResolvedFilters f, String expansionId,
                                          Integer minPrice, Integer maxPrice, Pageable pageable) {
        String resolvedSort = resolveSort(sort);
        if (CardRepository.SORT_NAME.equals(resolvedSort)) {
            return cardRepository.searchByNameOrderByName(keyword, f.hasTypes(), f.types(), f.hasRarities(), f.rarities(),
                    f.hasLanguages(), f.languages(), f.hasPrice(), minPrice, maxPrice, expansionId, pageable);
        }
        if (CardRepository.SORT_POPULAR.equals(resolvedSort)) {
            return cardRepository.searchByNameOrderByPopular(keyword, f.hasTypes(), f.types(), f.hasRarities(), f.rarities(),
                    f.hasLanguages(), f.languages(), f.hasPrice(), minPrice, maxPrice, expansionId, pageable);
        }
        return cardRepository.searchByNameOrderByLatest(keyword, f.hasTypes(), f.types(), f.hasRarities(), f.rarities(),
                f.hasLanguages(), f.languages(), f.hasPrice(), minPrice, maxPrice, expansionId, pageable);
    }

    // 한글 검색어를 도감번호 목록으로 변환해 조회한다. 매핑이 없으면 예외 대신 빈 페이지를 반환한다.
    // 검색어가 자음(초성)으로만 이뤄져 있으면 초성 검색, 아니면 이름 부분일치로 검색한다.
    // 한글/초성 검색은 도감번호(national_pokedex_numbers) 매핑 기반이라 포켓몬 카드만 지원한다.
    // 도감번호가 없는 트레이너/에너지 카드는 이 경로로 검색되지 않는다 - 의도된 한계
    // (PokeAPI 도감번호 매핑 방식의 알려진 제약).
    //
    // #308: 필터 적용 여부는 "정확 도감번호 매핑이 존재하는가"(matches.isEmpty() 이전 단계)로 이미
    // 판가름난다 - 영문 경로처럼 별도 existsBy 확인이 필요 없다. 매핑이 존재하는데 카드 필터 결합
    // 후 0건이면 그게 곧 "필터가 세서 없는 것"(②)이고, 매핑 자체가 없어 유사도로 넘어간 경우(③)엔
    // fuzzyMatch가 이미 그 단계에서 true로 정해져 있으므로 이후 필터 결과 건수와 무관하게 유지한다.
    private NameSearchResult searchByPokedexKoName(String keyword, List<String> types, List<String> rarities, List<String> languages,
                                                    String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        List<PokedexKoName> matches;
        boolean fuzzyMatch = false;
        if (KoreanTextUtil.isChosungOnly(keyword)) {
            matches = pokedexKoNameRepository.findByNameKoChosungContaining(keyword);
        } else {
            matches = pokedexKoNameRepository.findByNameKoContaining(keyword);
            // 정확 검색(부분일치)이 0건이고 키워드가 최소 길이 이상일 때만 유사도 검색으로 폴백한다(#187).
            // 초성 검색은 이미 자음 단위 매칭이라 대상에서 제외 - similarity()를 자음 문자열에 쓰는 건
            // 스파이크로 검증된 시나리오가 아니다.
            if (matches.isEmpty() && keyword.length() >= MIN_KEYWORD_LENGTH_FOR_SIMILARITY) {
                matches = pokedexKoNameRepository.findByNameKoSimilarTo(keyword, SIMILARITY_THRESHOLD);
                fuzzyMatch = !matches.isEmpty();
            }
        }
        if (matches.isEmpty()) {
            return new NameSearchResult(Page.empty(pageable), false);
        }
        List<Integer> pokedexNumbers = matches.stream().map(PokedexKoName::getPokedexNumber).toList();
        ResolvedFilters f = resolveFilters(types, rarities, languages, minPrice, maxPrice);
        Pageable unsortedPageable = stripSort(pageable);
        Page<Card> cards = dispatchPokedexExact(sort, pokedexNumbers, f, expansionId, minPrice, maxPrice, unsortedPageable);
        return new NameSearchResult(cards, fuzzyMatch);
    }

    private Page<Card> dispatchPokedexExact(String sort, List<Integer> pokedexNumbers, ResolvedFilters f, String expansionId,
                                             Integer minPrice, Integer maxPrice, Pageable pageable) {
        String resolvedSort = resolveSort(sort);
        if (CardRepository.SORT_NAME.equals(resolvedSort)) {
            return cardRepository.searchByPokedexNumbersOrderByName(pokedexNumbers, f.hasTypes(), f.types(), f.hasRarities(), f.rarities(),
                    f.hasLanguages(), f.languages(), f.hasPrice(), minPrice, maxPrice, expansionId, pageable);
        }
        if (CardRepository.SORT_POPULAR.equals(resolvedSort)) {
            return cardRepository.searchByPokedexNumbersOrderByPopular(pokedexNumbers, f.hasTypes(), f.types(), f.hasRarities(), f.rarities(),
                    f.hasLanguages(), f.languages(), f.hasPrice(), minPrice, maxPrice, expansionId, pageable);
        }
        return cardRepository.searchByPokedexNumbersOrderByLatest(pokedexNumbers, f.hasTypes(), f.types(), f.hasRarities(), f.rarities(),
                f.hasLanguages(), f.languages(), f.hasPrice(), minPrice, maxPrice, expansionId, pageable);
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
                .map(relatedCard -> toCardResponse(relatedCard, gradesByCardId, false))
                .toList();
    }

    private CardResponse toCardResponse(Card card, Map<Long, List<String>> gradesByCardId, boolean fuzzyMatch) {
        return CardResponse.from(card, gradesByCardId.getOrDefault(card.getId(), List.of()),
                cardNameKoResolver.resolve(card), CardTypeEnResolver.resolve(card.getTypes()),
                CardRarityResolver.resolve(card.getRarityCode(), card.getRarity()), fuzzyMatch);
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
