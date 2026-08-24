package com.pokade.domain.price.repository;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.entity.BuyOffer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuyOfferRepository extends JpaRepository<BuyOffer, Long> {

    // 마이페이지 "입찰" 섹션용 - 내가 buyer로 등록한 구매입찰 목록.
    Page<BuyOffer> findByBuyerId(Long buyerId, Pageable pageable);

    Page<BuyOffer> findByBuyerIdAndStatus(Long buyerId, String status, Pageable pageable);

    @Query("SELECT MAX(b.price) FROM BuyOffer b "
            + "WHERE b.cardId = :cardId AND b.variantId = :variantId AND b.status = 'ACTIVE'")
    Optional<Integer> findHighestActivePrice(@Param("cardId") Long cardId, @Param("variantId") Long variantId);

    // 여러 판본의 최고 구매호가를 한 번에 조회하기 위한 배치 버전(가격 요약 배치 조회용).
    @Query("SELECT b.variantId AS variantId, MAX(b.price) AS price FROM BuyOffer b "
            + "WHERE b.variantId IN :variantIds AND b.status = 'ACTIVE' GROUP BY b.variantId")
    List<VariantPriceView> findHighestActivePricesByVariantIds(@Param("variantIds") List<Long> variantIds);

    // 구매입찰 호가창 - domain.listing의 findOrderbook(매도 호가창)과 대응되는 매수 호가창.
    // 매수 쪽은 높은 값부터 체결 우선순위가 높으므로 가격 내림차순.
    @Query("SELECT b FROM BuyOffer b "
            + "WHERE b.cardId = :cardId AND b.variantId = :variantId AND b.status = 'ACTIVE' "
            + "AND (:grade IS NULL OR b.grade = :grade) "
            + "ORDER BY b.price DESC")
    List<BuyOffer> findOrderbook(@Param("cardId") Long cardId,
                                  @Param("variantId") Long variantId,
                                  @Param("grade") ListingGrade grade);

    interface VariantPriceView {
        Long getVariantId();
        Integer getPrice();
    }
}
