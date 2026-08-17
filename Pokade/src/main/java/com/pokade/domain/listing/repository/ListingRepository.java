package com.pokade.domain.listing.repository;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    // 탈퇴/정지 등으로 ACTIVE가 아닌 판매자의 매물은 구매자에게 노출하지 않는다 —
    // 탈퇴 정리(UserWithdrawalCleanupListener)가 아직 도착하기 전 아주 짧은 순간에도 안전하도록 조회 시점에 방어.
    @Query("SELECT l FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.status = :status "
            + "AND l.sellerId IN (SELECT u.id FROM User u WHERE u.status = com.pokade.domain.user.entity.type.UserStatus.ACTIVE) "
            + "ORDER BY l.price ASC")
    List<Listing> findByCardIdAndStatusOrderByPriceAsc(@Param("cardId") Long cardId,
                                                        @Param("status") ListingStatus status);

    @Query("SELECT l FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.variantId = :variantId AND l.status = :status "
            + "AND (:grade IS NULL OR l.grade = :grade) "
            + "AND l.sellerId IN (SELECT u.id FROM User u WHERE u.status = com.pokade.domain.user.entity.type.UserStatus.ACTIVE) "
            + "ORDER BY l.price ASC")
    List<Listing> findOrderbook(@Param("cardId") Long cardId,
                                 @Param("variantId") Long variantId,
                                 @Param("status") ListingStatus status,
                                 @Param("grade") ListingGrade grade);

    List<Listing> findBySellerId(Long sellerId);

    List<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status);

    long countBySellerIdAndStatus(Long sellerId, ListingStatus status);

    boolean existsBySellerIdAndCardIdAndVariantIdAndStatus(
            Long sellerId, Long cardId, Long variantId, ListingStatus status);

    @Query("SELECT MIN(l.price) FROM Listing l "
            + "WHERE l.cardId = :cardId AND l.variantId = :variantId AND l.status = :status")
    Optional<Integer> findLowestActivePrice(@Param("cardId") Long cardId,
                                             @Param("variantId") Long variantId,
                                             @Param("status") ListingStatus status);
    
    @Query("SELECT l.variantId AS variantId, MIN(l.price) AS price FROM Listing l "
            + "WHERE l.variantId IN :variantIds AND l.status = :status "
            + "AND (:grade IS NULL OR l.grade = :grade) "
            + "GROUP BY l.variantId")
    List<VariantPriceView> findLowestActivePricesByVariantIds(@Param("variantIds") List<Long> variantIds,
                                                               @Param("status") ListingStatus status,
                                                               @Param("grade") ListingGrade grade);

    interface VariantPriceView {
        Long getVariantId();
        Integer getPrice();
    }

    // ACTIVE 상태인 매물만 원자적으로 TRADING으로 전환. 반환값 0 = 이미 팔렸거나 존재하지 않음(동시 구매 충돌)
    @Modifying
    @Query("UPDATE Listing l SET l.status = com.pokade.domain.listing.entity.ListingStatus.TRADING "
            + "WHERE l.id = :id AND l.status = com.pokade.domain.listing.entity.ListingStatus.ACTIVE")
    int markAsTrading(@Param("id") Long listingId);

    // FR-TRADE-10: cutoff(등록 후 30일) 이전에 등록되었고 아직 알림을 안 보낸 ACTIVE 매물 조회
    List<Listing> findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(ListingStatus status, LocalDateTime cutoff);

    // 아직 HIDDEN이 아닌 매물만 원자적으로 HIDDEN으로 전환. 반환값 0 = 이미 숨김 처리됨(동시 숨김 처리 충돌)
    @Modifying
    @Query("UPDATE Listing l SET l.status = com.pokade.domain.listing.entity.ListingStatus.HIDDEN "
            + "WHERE l.id = :id AND l.status <> com.pokade.domain.listing.entity.ListingStatus.HIDDEN")
    int hideIfNotAlreadyHidden(@Param("id") Long listingId);
}
