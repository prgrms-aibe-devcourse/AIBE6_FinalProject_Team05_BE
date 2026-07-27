package com.pokade.domain.card.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pokade.domain.card.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    @Query(value = """
            SELECT c.* FROM cards c WHERE
            (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND
            (:types IS NULL OR CAST(:types AS text) = ANY(c.types)) AND
            (:rarity IS NULL OR c.rarity = :rarity) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            countQuery = """
            SELECT COUNT(*) FROM cards c WHERE
            (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND
            (:types IS NULL OR CAST(:types AS text) = ANY(c.types)) AND
            (:rarity IS NULL OR c.rarity = :rarity) AND
            (:expansionId IS NULL OR c.expansion_id = :expansionId)
            """,
            nativeQuery = true)
    Page<Card> search(@Param("name") String name,
                       @Param("types") String types,
                       @Param("rarity") String rarity,
                       @Param("expansionId") String expansionId,
                       Pageable pageable);
}