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

    // 필터 5종 조건절/검색 base·count SQL 원문은 CardSearchSql로 이동했다(#308 후속 - 상수가
    // 11개까지 쌓여 메서드 시그니처와 뒤섞이는 문제를 줄이기 위함). 상수 간 관계는 CardSearchSql의
    // 클래스 Javadoc에 정리돼 있다.

    @Query(value = CardSearchSql.CARD_SEARCH_BASE + "ORDER BY c.synced_at DESC, c.id DESC",
            countQuery = CardSearchSql.CARD_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> searchOrderByLatest(@Param("hasTypes") boolean hasTypes,
                                    @Param("types") String[] types,
                                    @Param("hasRarities") boolean hasRarities,
                                    @Param("rarities") List<String> rarities,
                                    @Param("hasLanguages") boolean hasLanguages,
                                    @Param("languages") List<String> languages,
                                    @Param("hasPrice") boolean hasPrice,
                                    @Param("minPrice") Integer minPrice,
                                    @Param("maxPrice") Integer maxPrice,
                                    @Param("expansionId") String expansionId,
                                    Pageable pageable);

    @Query(value = CardSearchSql.CARD_SEARCH_BASE + "ORDER BY c.name ASC, c.id ASC",
            countQuery = CardSearchSql.CARD_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> searchOrderByName(@Param("hasTypes") boolean hasTypes,
                                  @Param("types") String[] types,
                                  @Param("hasRarities") boolean hasRarities,
                                  @Param("rarities") List<String> rarities,
                                  @Param("hasLanguages") boolean hasLanguages,
                                  @Param("languages") List<String> languages,
                                  @Param("hasPrice") boolean hasPrice,
                                  @Param("minPrice") Integer minPrice,
                                  @Param("maxPrice") Integer maxPrice,
                                  @Param("expansionId") String expansionId,
                                  Pageable pageable);

    @Query(value = CardSearchSql.CARD_SEARCH_BASE + "ORDER BY c.view_count DESC, c.id DESC",
            countQuery = CardSearchSql.CARD_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> searchOrderByPopular(@Param("hasTypes") boolean hasTypes,
                                     @Param("types") String[] types,
                                     @Param("hasRarities") boolean hasRarities,
                                     @Param("rarities") List<String> rarities,
                                     @Param("hasLanguages") boolean hasLanguages,
                                     @Param("languages") List<String> languages,
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

    /**
     * languages 없이 호출하는 기존 오버로드 - #263 이전부터 있던 호출부(테스트 다수 포함)가
     * 인자 개수 때문에 깨지지 않도록 유지한다. 실제 language 필터는 8-인자 오버로드로 위임한다.
     */
    default Page<Card> search(List<String> types, List<String> rarities, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        return search(types, rarities, null, expansionId, minPrice, maxPrice, sort, pageable);
    }

    /** #263: language(언어 코드, 예 EN/JA) 필터가 추가된 검색. types/rarities와 동일한 방식(바인드 IN절, 값 화이트리스트 없음). */
    default Page<Card> search(List<String> types, List<String> rarities, List<String> languages, String expansionId, Integer minPrice, Integer maxPrice, String sort, Pageable pageable) {
        // 빈 문자열("")이 섞여 들어오면 실제 필터 조건 없이 IN ('') 비교만 남아 매칭이 전혀 안 되므로
        // hasTypes/hasRarities/hasLanguages 판단 전에 제거한다.
        List<String> filteredTypes = types == null ? null
                : types.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredRarities = rarities == null ? null
                : rarities.stream().filter(v -> v != null && !v.isBlank()).toList();
        List<String> filteredLanguages = languages == null ? null
                : languages.stream().filter(v -> v != null && !v.isBlank()).toList();

        boolean hasTypes = filteredTypes != null && !filteredTypes.isEmpty();
        boolean hasRarities = filteredRarities != null && !filteredRarities.isEmpty();
        boolean hasLanguages = filteredLanguages != null && !filteredLanguages.isEmpty();
        boolean hasPrice = minPrice != null || maxPrice != null;
        // Pageable에 담긴 Sort는 버린다: Spring의 Pageable 리졸버가 우리와 같은 "sort" 파라미터명을
        // 공유하기 때문에(예: ?sort=latest) 정렬 프로퍼티로 오인해 파싱해 넣을 수 있고, 그 값을 그대로
        // 네이티브 쿼리에 넘기면 이미 고정된 ORDER BY와 충돌한다. 정렬은 아래 화이트리스트로만 결정한다.
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        String resolvedSort = sort != null && SORT_COLUMN_WHITELIST.contains(sort) ? sort : SORT_LATEST;

        // types는 c.types && :types(overlap 연산자, GIN 인덱스 idx_cards_types_gin 활용)로 바인딩되므로
        // rarity/language처럼 "IN ('')로 무의미하게 채우는" 트릭이 아니라 실제 빈 배열을 넘긴다.
        String[] safeTypes = (hasTypes ? filteredTypes : List.<String>of()).toArray(String[]::new);
        List<String> safeRarities = hasRarities ? filteredRarities : List.of("");
        List<String> safeLanguages = hasLanguages ? filteredLanguages : List.of("");

        if (SORT_NAME.equals(resolvedSort)) {
            return searchOrderByName(hasTypes, safeTypes, hasRarities, safeRarities, hasLanguages, safeLanguages, hasPrice, minPrice, maxPrice, expansionId, unsortedPageable);
        }
        if (SORT_POPULAR.equals(resolvedSort)) {
            return searchOrderByPopular(hasTypes, safeTypes, hasRarities, safeRarities, hasLanguages, safeLanguages, hasPrice, minPrice, maxPrice, expansionId, unsortedPageable);
        }
        return searchOrderByLatest(hasTypes, safeTypes, hasRarities, safeRarities, hasLanguages, safeLanguages, hasPrice, minPrice, maxPrice, expansionId, unsortedPageable);
    }

    /**
     * #308: 필터+키워드 통합 쿼리(NAME_EXACT_FILTER_BASE 등)에서 이 ILIKE 조건을 문자열로 재사용하기
     * 위해 파생 쿼리 대신 명시적 native @Query로 전환했다 - 파생 쿼리는 SQL 텍스트를 꺼내 쓸 수 없어서
     * CARD_FILTER_CONDITIONS처럼 조합하는 방식이 불가능했다. 시그니처(2-인자)와 부분일치·대소문자
     * 무시 동작은 기존과 동일하다. ORDER BY만 새로 명시(c.name ASC, c.id ASC) - 파생 쿼리 시절엔
     * Pageable의 Sort가 있으면 그대로 반영됐지만, 이 메서드의 실제 호출부(CardQueryService.searchByName())는
     * Sort 없는 Pageable만 넘겨왔으므로 결과 자체는 달라지지 않는다.
     */
    @Query(value = "SELECT c.* FROM cards c WHERE c.name ILIKE CONCAT('%', :name, '%') ORDER BY c.name ASC, c.id ASC",
            countQuery = "SELECT COUNT(*) FROM cards c WHERE c.name ILIKE CONCAT('%', :name, '%')",
            nativeQuery = true)
    Page<Card> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);

    /**
     * #308: "필터 없는 키워드 단독 정확일치가 원래 존재하는가"만 가볍게 확인하는 용도 - 필터 결합 검색이
     * 0건일 때, 그게 "필터가 세서 없는 것"(폴백 불필요)인지 "키워드 자체가 애매한 것"(폴백 필요)인지
     * 가르는 판단 기준으로 쓴다. Page/count 없이 boolean만 필요해 findByNameContainingIgnoreCase를
     * 재사용하는 대신 더 가벼운 존재 확인 쿼리로 별도 선언했다.
     */
    boolean existsByNameContainingIgnoreCase(String name);

    /**
     * 정확 검색(부분일치)이 0건일 때만 시도하는 오타 허용 폴백 검색(#187) - pg_trgm의 similarity()로
     * name과의 유사도가 threshold 이상인 카드를 유사도 내림차순으로 조회한다. V4 마이그레이션에서
     * 추가한 pg_trgm 확장 + GIN 인덱스(idx_cards_name_trgm)를 전제로 한다.
     */
    @Query(value = """
            SELECT c.* FROM cards c WHERE similarity(c.name, :keyword) >= :threshold
            ORDER BY similarity(c.name, :keyword) DESC, c.id ASC
            """,
            countQuery = "SELECT COUNT(*) FROM cards c WHERE similarity(c.name, :keyword) >= :threshold",
            nativeQuery = true)
    Page<Card> findByNameSimilarTo(@Param("keyword") String keyword, @Param("threshold") double threshold, Pageable pageable);

    @Query(value = CardSearchSql.NAME_EXACT_FILTER_BASE + "ORDER BY c.synced_at DESC, c.id DESC",
            countQuery = CardSearchSql.NAME_EXACT_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByNameOrderByLatest(@Param("keyword") String keyword,
                                          @Param("hasTypes") boolean hasTypes,
                                          @Param("types") String[] types,
                                          @Param("hasRarities") boolean hasRarities,
                                          @Param("rarities") List<String> rarities,
                                          @Param("hasLanguages") boolean hasLanguages,
                                          @Param("languages") List<String> languages,
                                          @Param("hasPrice") boolean hasPrice,
                                          @Param("minPrice") Integer minPrice,
                                          @Param("maxPrice") Integer maxPrice,
                                          @Param("expansionId") String expansionId,
                                          Pageable pageable);

    @Query(value = CardSearchSql.NAME_EXACT_FILTER_BASE + "ORDER BY c.name ASC, c.id ASC",
            countQuery = CardSearchSql.NAME_EXACT_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByNameOrderByName(@Param("keyword") String keyword,
                                        @Param("hasTypes") boolean hasTypes,
                                        @Param("types") String[] types,
                                        @Param("hasRarities") boolean hasRarities,
                                        @Param("rarities") List<String> rarities,
                                        @Param("hasLanguages") boolean hasLanguages,
                                        @Param("languages") List<String> languages,
                                        @Param("hasPrice") boolean hasPrice,
                                        @Param("minPrice") Integer minPrice,
                                        @Param("maxPrice") Integer maxPrice,
                                        @Param("expansionId") String expansionId,
                                        Pageable pageable);

    @Query(value = CardSearchSql.NAME_EXACT_FILTER_BASE + "ORDER BY c.view_count DESC, c.id DESC",
            countQuery = CardSearchSql.NAME_EXACT_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByNameOrderByPopular(@Param("keyword") String keyword,
                                           @Param("hasTypes") boolean hasTypes,
                                           @Param("types") String[] types,
                                           @Param("hasRarities") boolean hasRarities,
                                           @Param("rarities") List<String> rarities,
                                           @Param("hasLanguages") boolean hasLanguages,
                                           @Param("languages") List<String> languages,
                                           @Param("hasPrice") boolean hasPrice,
                                           @Param("minPrice") Integer minPrice,
                                           @Param("maxPrice") Integer maxPrice,
                                           @Param("expansionId") String expansionId,
                                           Pageable pageable);

    @Query(value = CardSearchSql.NAME_TRGM_FILTER_BASE + "ORDER BY similarity(c.name, :keyword) DESC, c.id ASC",
            countQuery = CardSearchSql.NAME_TRGM_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByNameSimilarToWithFilters(@Param("keyword") String keyword,
                                                 @Param("threshold") double threshold,
                                                 @Param("hasTypes") boolean hasTypes,
                                                 @Param("types") String[] types,
                                                 @Param("hasRarities") boolean hasRarities,
                                                 @Param("rarities") List<String> rarities,
                                                 @Param("hasLanguages") boolean hasLanguages,
                                                 @Param("languages") List<String> languages,
                                                 @Param("hasPrice") boolean hasPrice,
                                                 @Param("minPrice") Integer minPrice,
                                                 @Param("maxPrice") Integer maxPrice,
                                                 @Param("expansionId") String expansionId,
                                                 Pageable pageable);

    @Query(value = CardSearchSql.POKEDEX_SEARCH_BASE + "ORDER BY c.name ASC, c.id ASC",
            countQuery = CardSearchSql.POKEDEX_SEARCH_COUNT,
            nativeQuery = true)
    Page<Card> findByNationalPokedexNumbersIn(@Param("pokedexNumbers") List<Integer> pokedexNumbers, Pageable pageable);

    @Query(value = CardSearchSql.POKEDEX_SEARCH_FILTER_BASE + "ORDER BY c.synced_at DESC, c.id DESC",
            countQuery = CardSearchSql.POKEDEX_SEARCH_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByPokedexNumbersOrderByLatest(@Param("pokedexNumbers") List<Integer> pokedexNumbers,
                                                    @Param("hasTypes") boolean hasTypes,
                                                    @Param("types") String[] types,
                                                    @Param("hasRarities") boolean hasRarities,
                                                    @Param("rarities") List<String> rarities,
                                                    @Param("hasLanguages") boolean hasLanguages,
                                                    @Param("languages") List<String> languages,
                                                    @Param("hasPrice") boolean hasPrice,
                                                    @Param("minPrice") Integer minPrice,
                                                    @Param("maxPrice") Integer maxPrice,
                                                    @Param("expansionId") String expansionId,
                                                    Pageable pageable);

    @Query(value = CardSearchSql.POKEDEX_SEARCH_FILTER_BASE + "ORDER BY c.name ASC, c.id ASC",
            countQuery = CardSearchSql.POKEDEX_SEARCH_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByPokedexNumbersOrderByName(@Param("pokedexNumbers") List<Integer> pokedexNumbers,
                                                  @Param("hasTypes") boolean hasTypes,
                                                  @Param("types") String[] types,
                                                  @Param("hasRarities") boolean hasRarities,
                                                  @Param("rarities") List<String> rarities,
                                                  @Param("hasLanguages") boolean hasLanguages,
                                                  @Param("languages") List<String> languages,
                                                  @Param("hasPrice") boolean hasPrice,
                                                  @Param("minPrice") Integer minPrice,
                                                  @Param("maxPrice") Integer maxPrice,
                                                  @Param("expansionId") String expansionId,
                                                  Pageable pageable);

    @Query(value = CardSearchSql.POKEDEX_SEARCH_FILTER_BASE + "ORDER BY c.view_count DESC, c.id DESC",
            countQuery = CardSearchSql.POKEDEX_SEARCH_FILTER_COUNT,
            nativeQuery = true)
    Page<Card> searchByPokedexNumbersOrderByPopular(@Param("pokedexNumbers") List<Integer> pokedexNumbers,
                                                     @Param("hasTypes") boolean hasTypes,
                                                     @Param("types") String[] types,
                                                     @Param("hasRarities") boolean hasRarities,
                                                     @Param("rarities") List<String> rarities,
                                                     @Param("hasLanguages") boolean hasLanguages,
                                                     @Param("languages") List<String> languages,
                                                     @Param("hasPrice") boolean hasPrice,
                                                     @Param("minPrice") Integer minPrice,
                                                     @Param("maxPrice") Integer maxPrice,
                                                     @Param("expansionId") String expansionId,
                                                     Pageable pageable);

    Optional<Card> findByExternalId(String externalId);

    // AI 등급 진단 이력 응답에서 vision_card_id(externalId)로 카드 정보를 배치 조회할 때 사용(N+1 방지).
    List<Card> findByExternalIdIn(List<String> externalIds);

    /**
     * 필터 옵션(Facet) API용 - 현재 cards에 실제로 존재하는 타입 값(원본 텍스트, 다국어 혼재)별로
     * 그 타입을 가진 카드 수를 조회한다(#263). types는 배열 컬럼이라 unnest로 펼친 뒤 카드 단위로
     * COUNT(DISTINCT card_id)해야 한다 - 같은 값이 한 카드 안에서 중복되는 경우까지 대비한 안전한 집계.
     */
    @Query(value = """
            SELECT t.val AS type, COUNT(DISTINCT c.id) AS count
            FROM cards c, unnest(c.types) AS t(val)
            GROUP BY t.val
            ORDER BY 1
            """, nativeQuery = true)
    List<CardTypeCountView> findTypeCounts();

    interface CardTypeCountView {
        String getType();
        Long getCount();
    }

    /**
     * 필터 옵션(Facet) API용 - 현재 cards에 실제로 존재하는 (rarity_code, rarity) 조합별 카드 수를 조회한다(#263).
     * CardRarityResolver.resolve()가 매핑 실패 시 원본 rarity로 폴백해야 하므로 rarity도 함께 조회하고,
     * rarity_code가 null인 카드도 원본 rarity로 폴백 노출되어야 하므로 WHERE로 제외하지 않는다.
     * 같은 표준 레어도로 리졸브되는 조합(예: 코드는 같은데 rarity 원본 텍스트만 다국어로 다른 경우)이
     * 여러 행으로 나뉠 수 있어, 표준 레어도 기준 합산은 서비스 레이어(CardService.getFacets())에서 한다.
     */
    @Query(value = "SELECT rarity_code AS rarityCode, rarity AS rarity, COUNT(*) AS count FROM cards GROUP BY rarity_code, rarity ORDER BY 1", nativeQuery = true)
    List<CardRarityView> findRarityCounts();

    interface CardRarityView {
        String getRarityCode();
        String getRarity();
        Long getCount();
    }

    /**
     * 필터 옵션(Facet) API용 - expansion_id별 카드 수를 조회한다(#263). expansion_id가 null인 카드는
     * 어느 세트 Facet에도 속하지 않으므로 제외한다.
     */
    @Query(value = "SELECT expansion_id AS expansionId, COUNT(*) AS count FROM cards WHERE expansion_id IS NOT NULL GROUP BY expansion_id", nativeQuery = true)
    List<ExpansionCardCountView> findCardCountsByExpansion();

    interface ExpansionCardCountView {
        String getExpansionId();
        Long getCount();
    }

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