package com.pokade.domain.card.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pokade.domain.card.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    @Query(value = """
            SELECT c.* FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            countQuery = """
            SELECT COUNT(*) FROM cards c WHERE
            (:hasTypes = false OR EXISTS (SELECT 1 FROM unnest(c.types) AS t(val) WHERE val IN (:types))) AND
            (:hasRarities = false OR c.rarity IN (:rarities)) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            nativeQuery = true)
    Page<Card> searchInternal(@Param("hasTypes") boolean hasTypes,
                               @Param("types") List<String> types,
                               @Param("hasRarities") boolean hasRarities,
                               @Param("rarities") List<String> rarities,
                               @Param("expansionId") String expansionId,
                               Pageable pageable);

    default Page<Card> search(List<String> types, List<String> rarities, String expansionId, Pageable pageable) {
        boolean hasTypes = types != null && !types.isEmpty();
        boolean hasRarities = rarities != null && !rarities.isEmpty();
        return searchInternal(
                hasTypes, hasTypes ? types : List.of(""),
                hasRarities, hasRarities ? rarities : List.of(""),
                expansionId,
                pageable);
    }

    Page<Card> findByNameContainingIgnoreCase(String name, Pageable pageable);

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