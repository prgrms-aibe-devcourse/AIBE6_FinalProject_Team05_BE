package com.pokade.domain.card.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokade.domain.card.entity.CardPrice;

public interface CardPriceRepository extends JpaRepository<CardPrice, Long> {

    Optional<CardPrice> findByVariantIdAndPriceTypeAndGradeAndCompany(
            Long variantId, String priceType, String grade, String company);
}
