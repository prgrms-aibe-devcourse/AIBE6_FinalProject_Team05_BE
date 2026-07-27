package com.pokade.domain.card.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokade.domain.card.entity.CardVariant;

public interface CardVariantRepository extends JpaRepository<CardVariant, Long> {

    List<CardVariant> findByCardIdOrderByPrimaryDescVariantNameAsc(Long cardId);
}
