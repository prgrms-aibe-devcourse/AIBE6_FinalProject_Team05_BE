package com.pokade.domain.card.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.ExpansionRepository;
import com.pokade.domain.card.support.CardRarityResolver;
import com.pokade.domain.card.support.CardTypeEnResolver;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

/**
 * 카드 필터 옵션(타입/레어도/세트) 집계만 담당한다. 검색/상세/유사 카드 조회는 CardQueryService로
 * 분리돼 있다. CardService(파사드)가 이 클래스와 CardQueryService를 감싼다.
 */
@Service
@RequiredArgsConstructor
public class CardFacetService {

    // series가 없는(null) 세트를 묶는 그룹 라벨 - FE 아코디언에서 "기타" 그룹으로 표시된다.
    private static final String UNKNOWN_SERIES_LABEL = "기타";

    private final CardRepository cardRepository;
    private final ExpansionRepository expansionRepository;

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
}
