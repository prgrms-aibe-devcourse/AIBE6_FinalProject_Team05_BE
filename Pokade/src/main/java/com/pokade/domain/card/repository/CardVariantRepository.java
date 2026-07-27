package com.pokade.domain.card.repository;

import com.pokade.domain.card.entity.CardVariant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardVariantRepository extends JpaRepository<CardVariant, Long> {

    @Query("SELECT cv.id FROM CardVariant cv WHERE cv.card.id = :cardId AND cv.primary = true")
    Optional<Long> findPrimaryVariantId(@Param("cardId") Long cardId);
}
