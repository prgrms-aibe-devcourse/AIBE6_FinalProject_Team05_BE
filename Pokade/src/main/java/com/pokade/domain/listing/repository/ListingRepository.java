package com.pokade.domain.listing.repository;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    // ACTIVE 상태인 매물만 원자적으로 TRADING으로 전환. 반환값 0 = 이미 팔렸거나 존재하지 않음(동시 구매 충돌)
    @Modifying
    @Query("UPDATE Listing l SET l.status = com.pokade.domain.listing.entity.ListingStatus.TRADING "
            + "WHERE l.id = :id AND l.status = com.pokade.domain.listing.entity.ListingStatus.ACTIVE")
    int markAsTrading(@Param("id") Long listingId);
}
