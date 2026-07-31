package com.pokade.domain.listing.repository;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findByCardIdAndStatusOrderByPriceAsc(Long cardId, ListingStatus status);

    @Query("SELECT l FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.variantId = :variantId AND l.status = :status "
            + "AND (:grade IS NULL OR l.grade = :grade) "
            + "ORDER BY l.price ASC")
    List<Listing> findOrderbook(@Param("cardId") Long cardId,
                                 @Param("variantId") Long variantId,
                                 @Param("status") ListingStatus status,
                                 @Param("grade") ListingGrade grade);

    List<Listing> findBySellerId(Long sellerId);

    List<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status);

    boolean existsBySellerIdAndCardIdAndVariantIdAndStatus(
            Long sellerId, Long cardId, Long variantId, ListingStatus status);

    @Query("SELECT MIN(l.price) FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.variantId = :variantId AND l.status = :status")
    Optional<Integer> findLowestActivePrice(@Param("cardId") Long cardId,
                                             @Param("variantId") Long variantId,
                                             @Param("status") ListingStatus status);

    // 여러 판본의 최저 매물가를 한 번에 조회하기 위한 배치 버전(가격 요약 배치 조회용) — variantId가
    // card_variants.id 그 자체라 카드별로 별도 필터링 없이 variantId만으로 그룹핑해도 충분하다.
    @Query("SELECT l.variantId AS variantId, MIN(l.price) AS price FROM Listing l "
            + "WHERE l.variantId IN :variantIds AND l.status = :status GROUP BY l.variantId")
    List<VariantPriceView> findLowestActivePricesByVariantIds(@Param("variantIds") List<Long> variantIds,
                                                               @Param("status") ListingStatus status);

    interface VariantPriceView {
        Long getVariantId();
        Integer getPrice();
    }

    // ACTIVE 상태인 매물만 원자적으로 TRADING으로 전환. 반환값 0 = 이미 팔렸거나 존재하지 않음(동시 구매 충돌)
    @Modifying
    @Query("UPDATE Listing l SET l.status = com.pokade.domain.listing.entity.ListingStatus.TRADING "
            + "WHERE l.id = :id AND l.status = com.pokade.domain.listing.entity.ListingStatus.ACTIVE")
    int markAsTrading(@Param("id") Long listingId);
}
