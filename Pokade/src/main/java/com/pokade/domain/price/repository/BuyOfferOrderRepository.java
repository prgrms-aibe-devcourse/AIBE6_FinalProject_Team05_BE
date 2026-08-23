package com.pokade.domain.price.repository;

import com.pokade.domain.price.entity.BuyOfferOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface BuyOfferOrderRepository extends JpaRepository<BuyOfferOrder, Long> {

    Optional<BuyOfferOrder> findByOrderId(String orderId);

    // TradeOrderRepository.markFailedIfPending()와 동일한 이유로 REQUIRES_NEW - 토스 승인 실패를
    // 호출자(confirmPurchase)가 예외로 다시 던지는 도중이라 바깥 트랜잭션은 결국 롤백된다.
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE BuyOfferOrder o SET o.status = com.pokade.domain.price.entity.BuyOfferOrderStatus.FAILED "
            + "WHERE o.orderId = :orderId AND o.status = com.pokade.domain.price.entity.BuyOfferOrderStatus.PENDING")
    int markFailedIfPending(@Param("orderId") String orderId);
}
