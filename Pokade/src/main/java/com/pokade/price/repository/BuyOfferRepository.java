package com.pokade.price.repository;

import com.pokade.price.entity.BuyOffer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuyOfferRepository extends JpaRepository<BuyOffer, Long> {

    @Query("SELECT MAX(b.price) FROM BuyOffer b "
            + "WHERE b.cardId = :cardId AND b.variantId = :variantId AND b.status = 'ACTIVE'")
    Optional<Integer> findHighestActivePrice(@Param("cardId") Long cardId, @Param("variantId") Long variantId);
}
