package com.pokade.domain.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findByCardIdAndStatusOrderByPriceAsc(Long cardId, ListingStatus status);

    List<Listing> findBySellerId(Long sellerId);

    List<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status);

    boolean existsBySellerIdAndCardIdAndVariantIdAndStatus(
            Long sellerId, Long cardId, Long variantId, ListingStatus status);

    @Query("SELECT MIN(l.price) FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.variantId = :variantId AND l.status = :status")
    Optional<Integer> findLowestActivePrice(@Param("cardId") Long cardId,
                                             @Param("variantId") Long variantId,
                                             @Param("status") ListingStatus status);
}
