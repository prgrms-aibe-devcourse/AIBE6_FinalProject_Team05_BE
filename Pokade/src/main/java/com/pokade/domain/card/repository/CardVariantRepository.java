package com.pokade.domain.card.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pokade.domain.card.entity.CardVariant;

public interface CardVariantRepository extends JpaRepository<CardVariant, Long> {

    List<CardVariant> findByCardIdOrderByPrimaryDescVariantNameAsc(Long cardId);

    @Query("SELECT cv.id FROM CardVariant cv WHERE cv.card.id = :cardId AND cv.primary = true")
    Optional<Long> findPrimaryVariantId(@Param("cardId") Long cardId);
    
    @Query("SELECT cv.card.id AS cardId, cv.id AS variantId FROM CardVariant cv "
            + "WHERE cv.card.id IN :cardIds AND cv.primary = true")
    List<PrimaryVariantIdView> findPrimaryVariantIdsByCardIds(@Param("cardIds") List<Long> cardIds);

    interface PrimaryVariantIdView {
        Long getCardId();
        Long getVariantId();
    }

    /**
     * 카드 상세조회용 variant별 등급(S/A/B) 매핑을 쿼리 1회로 조회한다.
     * listings.variant_id가 NULL인 매물은 대표 변형(is_primary) 기준 매물이므로 COALESCE로 합산한다.
     */
    @Query(value = """
            SELECT COALESCE(l.variant_id, cv.id) AS variantId, l.grade AS grade
            FROM listings l
            JOIN card_variants cv ON cv.card_id = l.card_id AND cv.is_primary = true
            WHERE l.card_id = :cardId
              AND l.status = 'ACTIVE'
              AND l.grade IN ('S', 'A', 'B')
            """,
            nativeQuery = true)
    List<VariantGradeView> findGradesByCardId(@Param("cardId") Long cardId);

    interface VariantGradeView {
        Long getVariantId();
        String getGrade();
    }
}
