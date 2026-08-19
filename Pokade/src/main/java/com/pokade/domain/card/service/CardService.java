package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.ExpansionRepository;
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
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

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
    private final PokedexKoNameRepository pokedexKoNameRepository;
    private final ExpansionRepository expansionRepository;
    private final CardNameKoResolver cardNameKoResolver;

    // Actuator/Prometheus 로컬 실험용 계측 - 커밋 대상 아님.
    // final이 아니라 Lombok @RequiredArgsConstructor 생성 대상에서 빠져 기존 테스트(@InjectMocks) 영향 없음.
    // required = false: @DataJpaTest 등 슬라이스 테스트엔 MeterRegistry 빈이 없어 NoSuchBeanDefinitionException으로
    // 컨텍스트 로딩 자체가 깨졌다(#224). 매칭되는 빈이 없으면 Spring이 필드를 건드리지 않고 그대로 두므로
    // (value == null이면 field.set() 자체를 안 함), 아래 기본값(SimpleMeterRegistry)이 계속 살아남아 null이 되지 않는다.
    @Autowired(required = false)
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // 임시 계측 - #217, 팀 논의 전 커밋 대상 아님
    @Timed(value = "card.search.duration")
    @Transactional(readOnly = true)
    public Page<CardResponse> search(List<String> types, List<String> rarities, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        validateFilterSize(types, "types");
        validateFilterSize(rarities, "rarity");
        validatePriceRange(minPrice, maxPrice);
        List<String> expandedTypes = CardTypeEnResolver.resolveOriginalValues(types);
        List<String> expandedRarities = CardRarityResolver.resolveOriginalValues(rarities);
        Page<Card> cards = cardRepository.search(expandedTypes, expandedRarities, expansionId, minPrice, maxPrice, sort, pageable);
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

    // series가 없는(null) 세트를 묶는 그룹 라벨 - FE 아코디언에서 "기타" 그룹으로 표시된다.
    private static final String UNKNOWN_SERIES_LABEL = "기타";

    /**
     * 카드 필터 옵션(타입/레어도/세트)을 DB에 실제로 존재하는 값과 그 값을 가진 카드 수 기준으로 조회한다(#263).
     * types/rarity_code는 원본이 다국어로 혼재돼 있어, 리졸버로 표준명으로 합친 뒤 표준명 기준으로 카드 수를
     * 합산한다(예: 일본어 "草" 카운트와 영문 "Grass" 카운트가 함께 있으면 리졸버를 거쳐 "Grass" 하나로
     * 합산됨). 세트명은 아직 언어별 표준화가 없어 원본 그대로 반환한다.
     *
     * 개수는 "다른 필터를 적용하지 않은 전체 기준 고정 개수"다 - 예를 들어 특정 세트를 이미 선택한 상태에서
     * 레어도별 개수가 그 세트 내로 좁혀지는 동적 집계(선택 조합별 계산)는 캐싱이 불가능해지고 쿼리도
     * 훨씬 복잡해져 별도 이슈로 분리한다. 이 전체 기준 집계는 세트/타입/레어도 조합과 무관하게 하나의
     * 스냅샷으로 캐싱 가능하므로 기존 TTL 1시간(CacheConfig의 "cardFacets" 캐시 설정) 전략을 그대로 유지한다.
     */
    @Cacheable(cacheNames = "cardFacets")
    @Transactional(readOnly = true)
    public CardFacetsResponse getFacets() {
        Map<String, Long> typeCounts = new TreeMap<>();
        for (CardRepository.CardTypeCountView view : cardRepository.findTypeCounts()) {
            if (view.getType() == null) {
                continue;
            }
            String resolvedType = CardTypeEnResolver.resolve(List.of(view.getType())).get(0);
            typeCounts.merge(resolvedType, view.getCount(), Long::sum);
        }

        Map<String, Long> rarityCounts = new TreeMap<>();
        for (CardRepository.CardRarityView view : cardRepository.findRarityCounts()) {
            // rarity_code와 rarity가 둘 다 null인 카드가 있으면 resolve()가 null을 반환하는데,
            // TreeMap 키로 null을 넣으면 자연순서 비교 시 NullPointerException을 던지므로 여기서 걸러낸다.
            String resolvedRarity = CardRarityResolver.resolve(view.getRarityCode(), view.getRarity());
            if (resolvedRarity != null) {
                rarityCounts.merge(resolvedRarity, view.getCount(), Long::sum);
            }
        }

        List<CardFacetsResponse.FacetOption> types = toFacetOptions(typeCounts);
        List<CardFacetsResponse.FacetOption> rarities = toFacetOptions(rarityCounts);
        List<CardFacetsResponse.ExpansionFacet> expansions = buildExpansionFacets();
        return CardFacetsResponse.of(types, rarities, expansions);
    }

    private List<CardFacetsResponse.FacetOption> toFacetOptions(Map<String, Long> countsByValue) {
        return countsByValue.entrySet().stream()
                .map(entry -> new CardFacetsResponse.FacetOption(entry.getKey(), entry.getValue()))
                .toList();
    }

    // expansions.name/series가 NULL인 레거시/수동 적재 데이터가 있을 수 있어, FE 응답 스키마(non-null)를
    // 깨지 않도록 각각 빈 문자열/"기타"로 대체한다. FE가 series로 묶어 아코디언 렌더링을 하기 편하도록,
    // 목록 자체를 "series 그룹의 최신 발매일(release_date) 내림차순 -> 그룹 내 세트명 오름차순"으로 정렬해
    // 반환한다(같은 series가 여러 해에 걸쳐 발매될 수 있어 그룹의 대표값은 MIN이 아닌 MAX release_date를 쓴다.
    // release_date가 전부 NULL인 series는 가장 오래된 것으로 취급해 뒤로 보낸다).
    private List<CardFacetsResponse.ExpansionFacet> buildExpansionFacets() {
        List<Expansion> allExpansions = expansionRepository.findAll();
        Map<String, Long> cardCountByExpansionId = cardRepository.findCardCountsByExpansion().stream()
                .collect(Collectors.toMap(CardRepository.ExpansionCardCountView::getExpansionId, CardRepository.ExpansionCardCountView::getCount));

        Map<String, LocalDate> latestReleaseBySeries = allExpansions.stream()
                .collect(Collectors.groupingBy(
                        expansion -> Objects.requireNonNullElse(expansion.getSeries(), UNKNOWN_SERIES_LABEL),
                        Collectors.mapping(Expansion::getReleaseDate,
                                Collectors.collectingAndThen(Collectors.toList(), dates -> dates.stream()
                                        .filter(Objects::nonNull)
                                        .max(Comparator.naturalOrder())
                                        .orElse(LocalDate.MIN)))));

        return allExpansions.stream()
                .map(expansion -> new CardFacetsResponse.ExpansionFacet(
                        expansion.getId(),
                        Objects.requireNonNullElse(expansion.getName(), ""),
                        Objects.requireNonNullElse(expansion.getSeries(), UNKNOWN_SERIES_LABEL),
                        cardCountByExpansionId.getOrDefault(expansion.getId(), 0L)))
                .sorted(Comparator
                        .comparing((CardFacetsResponse.ExpansionFacet facet) -> latestReleaseBySeries.get(facet.series()))
                        .reversed()
                        .thenComparing(CardFacetsResponse.ExpansionFacet::name,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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