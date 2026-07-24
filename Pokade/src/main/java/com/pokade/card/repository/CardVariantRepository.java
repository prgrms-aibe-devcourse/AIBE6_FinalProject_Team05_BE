package com.pokade.card.repository;

import com.pokade.card.entity.CardVariant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardVariantRepository extends JpaRepository<CardVariant, Long> {

    @Query("SELECT cv.id FROM CardVariant cv WHERE cv.cardId = :cardId AND cv.isPrimary = true")
    Optional<Long> findPrimaryVariantId(@Param("cardId") Long cardId);
}
