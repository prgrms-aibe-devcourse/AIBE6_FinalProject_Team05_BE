package com.pokade.price.repository;

import com.pokade.price.entity.Listing;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    @Query("SELECT MIN(l.price) FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.variantId = :variantId AND l.status = 'ACTIVE'")
    Optional<Integer> findLowestActivePrice(@Param("cardId") Long cardId, @Param("variantId") Long variantId);
}
