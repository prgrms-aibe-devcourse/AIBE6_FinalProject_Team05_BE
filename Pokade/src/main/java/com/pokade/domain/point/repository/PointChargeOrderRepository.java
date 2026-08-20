package com.pokade.domain.point.repository;

import com.pokade.domain.point.entity.PointChargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PointChargeOrderRepository extends JpaRepository<PointChargeOrder, Long> {

    Optional<PointChargeOrder> findByOrderId(String orderId);

    // 토스 승인 실패를 기록하는 용도 - 호출하는 PointChargeService.confirm()이 이 실패를 호출자에게
    // 다시 던지는 도중이라 그 트랜잭션은 결국 롤백된다. 같은 트랜잭션 안에서 엔티티만 바꿔서는 커밋되지
    // 않으므로, 별도 빈(리포지토리)의 REQUIRES_NEW로 이 UPDATE만 즉시 커밋한다.
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE PointChargeOrder o SET o.status = com.pokade.domain.point.entity.PointChargeOrderStatus.FAILED "
            + "WHERE o.orderId = :orderId AND o.status = com.pokade.domain.point.entity.PointChargeOrderStatus.PENDING")
    int markFailedIfPending(@Param("orderId") String orderId);
}
