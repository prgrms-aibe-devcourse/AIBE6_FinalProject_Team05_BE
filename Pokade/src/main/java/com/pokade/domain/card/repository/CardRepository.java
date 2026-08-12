package com.pokade.domain.card.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    String SORT_LATEST = "latest";
    String SORT_NAME = "name";
    String SORT_POPULAR = "popular";

    /** 카드 목록/연관 카드 조회의 기본 페이지 크기·상한 개수. */
    int DEFAULT_PAGE_SIZE = 20;

    /**
     * listings.status가 ACTIVE(매물 도메인 {@code com.pokade.domain.listing.entity.ListingStatus.ACTIVE})인
     * 매물만 조회할 때 쓰는 네이티브 쿼리 리터럴. status 컬럼은 {@code @Enumerated(EnumType.STRING)}이라
     * "ACTIVE"라는 문자열이 실제 저장값과 같지만, 이 상수는 그 값을 그대로 베낀 것일 뿐 enum과 타입으로
     * 연결되어 있지 않다. 따라서 ListingStatus.ACTIVE의 이름이 바뀌면 컴파일 에러 없이 이 값만 조용히
     * 어긋날 수 있다 — 상수화가 그 위험 자체를 없애주지는 않는다.
     */
    String LISTING_STATUS_ACTIVE = "ACTIVE";

    /**
     * sort 요청 파라미터 화이트리스트. 값은 아래 default search()의 if/else 디스패치에서
     * 어떤 @Query를 실행할지 고르는 키로만 쓰이고 SQL 문자열에 직접 삽입되지 않으므로
     * (고정된 세 개의 @Query 중 택일), 인젝션 여지가 없다.
     */
    Set<String> SORT_COLUMN_WHITELIST = Set.of(SORT_LATEST, SORT_NAME, SORT_POPULAR);

    /**
     * searchOrderBy* 3개 @Query가 공유하는 SELECT ~ WHERE 절 (ORDER BY 제외).
     * ORDER BY는 인덱스 정렬 최적화를 위해 메서드별로 분리 유지한다.
     */
    String CARD_SEARCH_BASE = """
            SELECT c.* FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:hasPrice = false OR EXISTS (
                SELECT 1 FROM listings l WHERE l.card_id = c.id AND l.status = '""" + LISTING_STATUS_ACTIVE + "'" + """
                AND (:minPrice IS NULL OR l.price >= :minPrice)
                AND (:maxPrice IS NULL OR l.price <= :maxPrice)
            )) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """;

    /** searchOrderBy* 3개 @Query가 공유하는 countQuery. ORDER BY가 없어 셋 다 동일하다. */
    String CARD_SEARCH_COUNT = """
            SELECT COUNT(*) FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:hasPrice = false OR EXISTS (
                SELECT 1 FROM listings l WHERE l.card_id = c.id AND l.status = '""" + LISTING_STATUS_ACTIVE + "'" + """
                AND (:minPrice IS NULL OR l.price >= :minPrice)
                AND (:maxPrice IS NULL OR l.price <= :maxPrice)
            )) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """;

    @Query(value = CARD_SEARCH_BASE + "ORDER BY c.synced_at DESC, c.id DESC",
            countQuery = CARD_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> searchOrderByLatest(@Param("hasTypes") boolean hasTypes,
                                    @Param("types") List<String> types,
                                    @Param("hasRarities") boolean hasRarities,
                                    @Param("rarities") List<String> rarities,
                                    @Param("hasPrice") boolean hasPrice,
                                    @Param("minPrice") Integer minPrice,
                                    @Param("maxPrice") Integer maxPrice,
                                    @Param("expansionId") String expansionId,
                                    Pageable pageable);

    @Query(value = CARD_SEARCH_BASE + "ORDER BY c.name ASC, c.id ASC",
            countQuery = CARD_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> searchOrderByName(@Param("hasTypes") boolean hasTypes,
                                  @Param("types") List<String> types,
                                  @Param("hasRarities") boolean hasRarities,
                                  @Param("rarities") List<String> rarities,
                                  @Param("hasPrice") boolean hasPrice,
                                  @Param("minPrice") Integer minPrice,
                                  @Param("maxPrice") Integer maxPrice,
                                  @Param("expansionId") String expansionId,
                                  Pageable pageable);

    @Query(value = CARD_SEARCH_BASE + "ORDER BY c.view_count DESC, c.id DESC",
            countQuery = CARD_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> searchOrderByPopular(@Param("hasTypes") boolean hasTypes,
                                     @Param("types") List<String> types,
                                     @Param("hasRarities") boolean hasRarities,
                                     @Param("rarities") List<String> rarities,
                                     @Param("hasPrice") boolean hasPrice,
                                     @Param("minPrice") Integer minPrice,
                                     @Param("maxPrice") Integer maxPrice,
                                     @Param("expansionId") String expansionId,
                                     Pageable pageable);

    @Transactional
    @Modifying
    @Query(value = "UPDATE cards SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementViewCount(@Param("id") Long id);

    /**
     * 카드별 ACTIVE 매물에 존재하는 등급(S/A/B) 집합을 배치로 조회한다.
     * 카드 목록 응답에 등급 정보를 붙일 때 카드 수와 무관하게 쿼리 1회로 처리하기 위해 사용한다.
     */
    @Query(value = """
            SELECT DISTINCT l.card_id AS cardId, l.grade AS grade
            FROM listings l
            WHERE l.card_id IN (:cardIds)
              AND l.status = '""" + LISTING_STATUS_ACTIVE + "'" + """
              AND l.grade IN (:validGrades)
            """,
            nativeQuery = true)
    List<CardGradeView> findGradesByCardIds(@Param("cardIds") List<Long> cardIds, @Param("validGrades") List<String> validGrades);

    interface CardGradeView {
        Long getCardId();
        String getGrade();
    }

    default Page<Card> search(List<String> types, List<String> rarities, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        // 빈 문자열("")이 섞여 들어오면 실제 필터 조건 없이 IN ('') 비교만 남아 매칭이 전혀 안 되므로
        // hasTypes/hasRarities 판단 전에 제거한다.
        List<String> filteredTypes = types == null ? null
                : types.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredRarities = rarities == null ? null
                : rarities.stream().filter(v -> v != null && !v.isBlank()).toList();

        boolean hasTypes = filteredTypes != null && !filteredTypes.isEmpty();
        boolean hasRarities = filteredRarities != null && !filteredRarities.isEmpty();
        boolean hasPrice = minPrice != null || maxPrice != null;
        // Pageable에 담긴 Sort는 버린다: Spring의 Pageable 리졸버가 우리와 같은 "sort" 파라미터명을
        // 공유하기 때문에(예: ?sort=latest) 정렬 프로퍼티로 오인해 파싱해 넣을 수 있고, 그 값을 그대로
        // 네이티브 쿼리에 넘기면 이미 고정된 ORDER BY와 충돌한다. 정렬은 아래 화이트리스트로만 결정한다.
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        String resolvedSort = sort != null && SORT_COLUMN_WHITELIST.contains(sort) ? sort : SORT_LATEST;

        List<String> safeTypes = hasTypes ? filteredTypes : List.of("");
        List<String> safeRarities = hasRarities ? filteredRarities : List.of("");

        if (SORT_NAME.equals(resolvedSort)) {
            return searchOrderByName(hasTypes, safeTypes, hasRarities, safeRarities, hasPrice, minPrice, maxPrice, expansionId, unsortedPageable);
        }
        if (SORT_POPULAR.equals(resolvedSort)) {
            return searchOrderByPopular(hasTypes, safeTypes, hasRarities, safeRarities, hasPrice, minPrice, maxPrice, expansionId, unsortedPageable);
        }
        return searchOrderByLatest(hasTypes, safeTypes, hasRarities, safeRarities, hasPrice, minPrice, maxPrice, expansionId, unsortedPageable);
    }

    Page<Card> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * 한글 검색어를 도감번호 목록으로 변환한 뒤(PokedexKoNameRepository, 부분일치/초성 검색이라 여러 건일 수 있음)
     * 조회하는 용도 - 배열 컬럼이라 unnest+IN으로 매칭한다(CARD_SEARCH_BASE의 types 필터와 동일한 패턴).
     */
    String POKEDEX_SEARCH_BASE = """
            SELECT c.* FROM cards c
            WHERE EXISTS (SELECT 1 FROM unnest(c.national_pokedex_numbers) AS n(val) WHERE val IN (:pokedexNumbers))
            """;

    /** findByNationalPokedexNumbersIn과 짝을 이루는 countQuery. ORDER BY가 없어 조건절이 동일하다. */
    String POKEDEX_SEARCH_COUNT = """
            SELECT COUNT(*) FROM cards c
            WHERE EXISTS (SELECT 1 FROM unnest(c.national_pokedex_numbers) AS n(val) WHERE val IN (:pokedexNumbers))
            """;

    @Query(value = POKEDEX_SEARCH_BASE + "ORDER BY c.name ASC, c.id ASC",
            countQuery = POKEDEX_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> findByNationalPokedexNumbersIn(@Param("pokedexNumbers") List<Integer> pokedexNumbers, Pageable pageable);

    Optional<Card> findByExternalId(String externalId);

    /** 필터 옵션(Facet) API용 - 현재 cards에 실제로 존재하는 타입 값 전체(원본 텍스트, 다국어 혼재)를 조회한다. */
    @Query(value = "SELECT DISTINCT unnest(types) FROM cards ORDER BY 1", nativeQuery = true)
    List<String> findDistinctTypes();

    /** 필터 옵션(Facet) API용 - 현재 cards에 실제로 존재하는 rarity_code 전체를 조회한다. */
    @Query(value = "SELECT DISTINCT rarity_code FROM cards WHERE rarity_code IS NOT NULL ORDER BY 1", nativeQuery = true)
    List<String> findDistinctRarityCodes();

    @Query(value = """
            SELECT c.* FROM cards c
            JOIN cards src ON src.id = :id
            WHERE c.id <> :id
            AND c.national_pokedex_numbers && src.national_pokedex_numbers
            ORDER BY c.name
            LIMIT\s""" + DEFAULT_PAGE_SIZE,
            nativeQuery = true)
    List<Card> findRelatedByPokedexNumber(@Param("id") Long id);

    @Query(value = """
            SELECT c.* FROM cards c
            WHERE c.id <> :excludeCardId
            AND c.expansion_id = :expansionId
            ORDER BY c.name
            LIMIT\s""" + DEFAULT_PAGE_SIZE,
            nativeQuery = true)
    List<Card> findRelatedByExpansion(@Param("expansionId") String expansionId, @Param("excludeCardId") Long excludeCardId);
}