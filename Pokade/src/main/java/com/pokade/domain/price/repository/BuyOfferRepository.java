package com.pokade.domain.price.repository;

import com.pokade.domain.price.entity.BuyOffer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuyOfferRepository extends JpaRepository<BuyOffer, Long> {

    @Query("SELECT MAX(b.price) FROM BuyOffer b "
            + "WHERE b.cardId = :cardId AND b.variantId = :variantId AND b.status = 'ACTIVE'")
    Optional<Integer> findHighestActivePrice(@Param("cardId") Long cardId, @Param("variantId") Long variantId);

    // 여러 판본의 최고 구매호가를 한 번에 조회하기 위한 배치 버전(가격 요약 배치 조회용).
    @Query("SELECT b.variantId AS variantId, MAX(b.price) AS price FROM BuyOffer b "
            + "WHERE b.variantId IN :variantIds AND b.status = 'ACTIVE' GROUP BY b.variantId")
    List<VariantPriceView> findHighestActivePricesByVariantIds(@Param("variantIds") List<Long> variantIds);

    interface VariantPriceView {
        Long getVariantId();
        Integer getPrice();
    }
}
