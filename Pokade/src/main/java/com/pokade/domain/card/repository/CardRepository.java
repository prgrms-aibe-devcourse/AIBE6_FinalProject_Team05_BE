package com.pokade.domain.card.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * sort 요청 파라미터 화이트리스트. 값은 실제 실행할 SQL을 선택하는 키로만 쓰이고
     * SQL 문자열에 직접 삽입되지 않으므로(고정된 두 개의 @Query 중 택일), 인젝션 여지가 없다.
     */
    Map<String, String> SORT_COLUMN_WHITELIST = Map.of(
            SORT_LATEST, "synced_at",
            SORT_NAME, "name",
            SORT_POPULAR, "view_count"
    );

    @Query(value = """
            SELECT c.* FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            ORDER BY c.synced_at DESC, c.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            nativeQuery = true)
    Page<Card> searchOrderByLatest(@Param("hasTypes") boolean hasTypes,
                                    @Param("types") List<String> types,
                                    @Param("hasRarities") boolean hasRarities,
                                    @Param("rarities") List<String> rarities,
                                    @Param("expansionId") String expansionId,
                                    Pageable pageable);

    @Query(value = """
            SELECT c.* FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            ORDER BY c.name ASC, c.id ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            nativeQuery = true)
    Page<Card> searchOrderByName(@Param("hasTypes") boolean hasTypes,
                                  @Param("types") List<String> types,
                                  @Param("hasRarities") boolean hasRarities,
                                  @Param("rarities") List<String> rarities,
                                  @Param("expansionId") String expansionId,
                                  Pageable pageable);

    @Query(value = """
            SELECT c.* FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            ORDER BY c.view_count DESC, c.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            nativeQuery = true)
    Page<Card> searchOrderByPopular(@Param("hasTypes") boolean hasTypes,
                                     @Param("types") List<String> types,
                                     @Param("hasRarities") boolean hasRarities,
                                     @Param("rarities") List<String> rarities,
                                     @Param("expansionId") String expansionId,
                                     Pageable pageable);

    @Transactional
    @Modifying
    @Query(value = "UPDATE cards SET view_count = view_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementViewCount(@Param("id") Long id);

    default Page<Card> search(List<String> types, List<String> rarities, String expansionId, String sort, Pageable pageable) {
        boolean hasTypes = types != null && !types.isEmpty();
        boolean hasRarities = rarities != null && !rarities.isEmpty();
        // Pageable에 담긴 Sort는 버린다: Spring의 Pageable 리졸버가 우리와 같은 "sort" 파라미터명을
        // 공유하기 때문에(예: ?sort=latest) 정렬 프로퍼티로 오인해 파싱해 넣을 수 있고, 그 값을 그대로
        // 네이티브 쿼리에 넘기면 이미 고정된 ORDER BY와 충돌한다. 정렬은 아래 화이트리스트로만 결정한다.
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        String resolvedSort = sort != null && SORT_COLUMN_WHITELIST.containsKey(sort) ? sort : SORT_LATEST;

        List<String> safeTypes = hasTypes ? types : List.of("");
        List<String> safeRarities = hasRarities ? rarities : List.of("");

        if (SORT_NAME.equals(resolvedSort)) {
            return searchOrderByName(hasTypes, safeTypes, hasRarities, safeRarities, expansionId, unsortedPageable);
        }
        if (SORT_POPULAR.equals(resolvedSort)) {
            return searchOrderByPopular(hasTypes, safeTypes, hasRarities, safeRarities, expansionId, unsortedPageable);
        }
        return searchOrderByLatest(hasTypes, safeTypes, hasRarities, safeRarities, expansionId, unsortedPageable);
    }

    Page<Card> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<Card> findByExternalId(String externalId);

    @Query(value = """
            SELECT c.* FROM cards c
            JOIN cards src ON src.id = :id
            WHERE c.id <> :id
            AND c.national_pokedex_numbers && src.national_pokedex_numbers
            ORDER BY c.name
            LIMIT 20
            """,
            nativeQuery = true)
    List<Card> findRelatedByPokedexNumber(@Param("id") Long id);

    @Query(value = """
            SELECT c.* FROM cards c
            WHERE c.id <> :excludeCardId
            AND c.expansion_id = :expansionId
            ORDER BY c.name
            LIMIT 20
            """,
            nativeQuery = true)
    List<Card> findRelatedByExpansion(@Param("expansionId") String expansionId, @Param("excludeCardId") Long excludeCardId);
}