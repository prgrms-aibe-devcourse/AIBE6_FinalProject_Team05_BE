package com.pokade.domain.card.repository;

/**
 * {@link CardRepository}의 native {@code @Query} 검색 쿼리 SQL 원문 상수만 모아둔 클래스(#308 후속
 * 리팩터링) - CardRepository.java에 상수 11개가 메서드 시그니처와 섞여 있어 메서드 하나를 이해하려면
 * 위쪽 상수 블록을 계속 스크롤해서 대조해야 하는 문제를 줄이기 위해 분리했다. CardRepository는 이제
 * "{@code @Query(value = CardSearchSql.XXX + "ORDER BY ...")}" 형태로 참조만 하고, SQL 원문은 전부
 * 여기 있다.
 *
 * <p>패키지 밖으로 노출할 이유가 없어 package-private으로 둔다({@link CardRepository}와 같은 패키지라
 * 접근에 문제없다). 인스턴스화하지 않는 순수 상수 홀더라 domain.watchlist.service.WatchlistVariantResolver와
 * 동일하게 private 생성자로 막는다.
 *
 * <h2>상수 간 관계 - 전부 아래 순서로 읽으면 됨</h2>
 * <ol>
 *   <li>{@link #CARD_FILTER_CONDITIONS} - 필터 5종(types/rarity/language/price/expansionId) 조건절
 *       본문. 이 아래 대부분의 상수가 이걸 그대로 이어붙인다 - 필터 규칙을 고치는 유지보수는 원칙적으로
 *       여기 한 곳만 고치면 된다.</li>
 *   <li>필터 전용(키워드 없음) - {@link #CARD_SEARCH_BASE}/{@link #CARD_SEARCH_COUNT}
 *       → {@code CardRepository.searchOrderBy*} 3종이 사용.</li>
 *   <li>영문 키워드 정확일치 + 필터 - {@link #NAME_EXACT_FILTER_BASE}/{@link #NAME_EXACT_FILTER_COUNT}
 *       → {@code CardRepository.searchByNameOrderBy*} 3종이 사용.</li>
 *   <li>영문 유사도(오타 허용) 폴백 + 필터 - {@link #NAME_TRGM_FILTER_BASE}/{@link #NAME_TRGM_FILTER_COUNT}
 *       → {@code CardRepository.searchByNameSimilarToWithFilters}가 사용.</li>
 *   <li>한글/도감번호 + 필터 - {@link #POKEDEX_SEARCH_FILTER_BASE}/{@link #POKEDEX_SEARCH_FILTER_COUNT}
 *       → {@code CardRepository.searchByPokedexNumbersOrderBy*} 3종이 사용.</li>
 * </ol>
 * 딱 하나, {@link #POKEDEX_SEARCH_BASE}/{@link #POKEDEX_SEARCH_COUNT}만 예외로
 * {@code CARD_FILTER_CONDITIONS}를 안 쓴다 - {@code #308} 이전부터 있던 필터 없는
 * {@code findByNationalPokedexNumbersIn()} 전용이고, 그 메서드/테스트를 그대로 유지하기 위해
 * 손대지 않았다.
 */
final class CardSearchSql {

    private CardSearchSql() {
    }

    /**
     * 필터 5종(types/rarity/language/price/expansionId) 조건절 본문("WHERE" 키워드 제외) - #308:
     * 필터+키워드 통합을 위해 CARD_SEARCH_BASE에서 분리했다. 이 상수 하나만 고치면 아래
     * CARD_SEARCH_BASE와 키워드 결합 검색(NAME_EXACT_FILTER_BASE/NAME_TRGM_FILTER_BASE/
     * POKEDEX_SEARCH_FILTER_BASE) 전체에 동일하게 반영된다.
     */
    static final String CARD_FILTER_CONDITIONS = """
            (:hasTypes = false OR c.types && CAST(:types AS text[])) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:hasLanguages = false OR c.language_code IN (:languages)) AND
            (:hasPrice = false OR EXISTS (
                SELECT 1 FROM listings l WHERE l.card_id = c.id AND l.status = '""" + CardRepository.LISTING_STATUS_ACTIVE + "'" + """
                AND (:minPrice IS NULL OR l.price >= :minPrice)
                AND (:maxPrice IS NULL OR l.price <= :maxPrice)
            )) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """;

    // ===== 필터 전용(키워드 없음) - CardRepository.searchOrderBy* 3종 =====

    /** searchOrderBy* 3개 @Query가 공유하는 SELECT ~ WHERE 절 (ORDER BY 제외). */
    static final String CARD_SEARCH_BASE = "SELECT c.* FROM cards c WHERE " + CARD_FILTER_CONDITIONS;

    /** searchOrderBy* 3개 @Query가 공유하는 countQuery. ORDER BY가 없어 셋 다 동일하다. */
    static final String CARD_SEARCH_COUNT = "SELECT COUNT(*) FROM cards c WHERE " + CARD_FILTER_CONDITIONS;

    // ===== 영문 키워드 정확일치 + 필터 - CardRepository.searchByNameOrderBy* 3종 =====

    /**
     * #308: 영문 키워드 정확일치(ILIKE 부분일치) + 필터 5종 결합 검색의 SELECT~WHERE(ORDER BY 제외).
     * CARD_FILTER_CONDITIONS를 재사용해 CARD_SEARCH_BASE와 동일한 필터 조건을 적용한다.
     */
    static final String NAME_EXACT_FILTER_BASE = """
            SELECT c.* FROM cards c WHERE
            c.name ILIKE CONCAT('%', :keyword, '%') AND
            """ + CARD_FILTER_CONDITIONS;

    /** searchByNameOrderBy* 3개 @Query가 공유하는 countQuery. */
    static final String NAME_EXACT_FILTER_COUNT = """
            SELECT COUNT(*) FROM cards c WHERE
            c.name ILIKE CONCAT('%', :keyword, '%') AND
            """ + CARD_FILTER_CONDITIONS;

    // ===== 영문 유사도(오타 허용) 폴백 + 필터 - CardRepository.searchByNameSimilarToWithFilters =====

    /**
     * #308: 오타 허용 유사도 폴백(#187) + 필터 5종 결합. 폴백의 존재 이유가 "유사도 순 추천"이라
     * sort 파라미터와 무관하게 유사도 DESC로 고정한다(설계 승인 항목: 유사도 폴백은 정렬 고정) -
     * 그래서 이 base는 searchByNameOrderBy*와 달리 정렬별로 3벌 나뉘지 않고 하나뿐이다.
     */
    static final String NAME_TRGM_FILTER_BASE = """
            SELECT c.* FROM cards c WHERE
            similarity(c.name, :keyword) >= :threshold AND
            """ + CARD_FILTER_CONDITIONS;

    static final String NAME_TRGM_FILTER_COUNT = """
            SELECT COUNT(*) FROM cards c WHERE
            similarity(c.name, :keyword) >= :threshold AND
            """ + CARD_FILTER_CONDITIONS;

    // ===== 한글/도감번호(필터 없음) - CardRepository.findByNationalPokedexNumbersIn =====
    // #308 이전부터 있던 필터 없는 전용 경로 - 위 CARD_FILTER_CONDITIONS 계열과 무관하다(유일한 예외).

    /**
     * 한글 검색어를 도감번호 목록으로 변환한 뒤(PokedexKoNameRepository, 부분일치/초성 검색이라 여러 건일 수 있음)
     * 조회하는 용도 - 배열 컬럼이라 unnest+IN으로 매칭한다. #300 전에는 CARD_SEARCH_BASE의 types 필터도
     * 이 패턴이었으나, GIN 인덱스(idx_cards_types_gin)를 실제로 타게 하려고 types는 overlap 연산자(&&)로
     * 재작성했다 - national_pokedex_numbers는 이번 변경 범위 밖이라 이 패턴을 그대로 유지한다.
     */
    static final String POKEDEX_SEARCH_BASE = """
            SELECT c.* FROM cards c
            WHERE EXISTS (SELECT 1 FROM unnest(c.national_pokedex_numbers) AS n(val) WHERE val IN (:pokedexNumbers))
            """;

    /** findByNationalPokedexNumbersIn과 짝을 이루는 countQuery. ORDER BY가 없어 조건절이 동일하다. */
    static final String POKEDEX_SEARCH_COUNT = """
            SELECT COUNT(*) FROM cards c
            WHERE EXISTS (SELECT 1 FROM unnest(c.national_pokedex_numbers) AS n(val) WHERE val IN (:pokedexNumbers))
            """;

    // ===== 한글/도감번호 + 필터 - CardRepository.searchByPokedexNumbersOrderBy* 3종 =====

    /**
     * #308: 한글 키워드(도감번호 매핑) + 필터 5종 결합 검색의 SELECT~WHERE(ORDER BY 제외).
     * POKEDEX_SEARCH_BASE는 필터 없는 기존 findByNationalPokedexNumbersIn() 전용으로 그대로 두고,
     * 이 상수를 따로 둬서 CARD_FILTER_CONDITIONS를 결합한다 - 기존 메서드/테스트는 영향받지 않는다.
     */
    static final String POKEDEX_SEARCH_FILTER_BASE = """
            SELECT c.* FROM cards c WHERE
            EXISTS (SELECT 1 FROM unnest(c.national_pokedex_numbers) AS n(val) WHERE val IN (:pokedexNumbers)) AND
            """ + CARD_FILTER_CONDITIONS;

    /** searchByPokedexNumbersOrderBy* 3개 @Query가 공유하는 countQuery. */
    static final String POKEDEX_SEARCH_FILTER_COUNT = """
            SELECT COUNT(*) FROM cards c WHERE
            EXISTS (SELECT 1 FROM unnest(c.national_pokedex_numbers) AS n(val) WHERE val IN (:pokedexNumbers)) AND
            """ + CARD_FILTER_CONDITIONS;
}
