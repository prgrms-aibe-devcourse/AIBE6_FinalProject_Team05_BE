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
}
