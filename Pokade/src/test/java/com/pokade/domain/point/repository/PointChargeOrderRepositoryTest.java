package com.pokade.domain.point.repository;

import com.pokade.domain.point.entity.PointChargeOrder;
import com.pokade.domain.point.entity.PointChargeOrderStatus;
import com.pokade.domain.user.entity.User;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest는 각 테스트를 롤백되는 트랜잭션으로 감싼다. markFailedIfPending()은 REQUIRES_NEW라
// 완전히 별도의 커넥션/트랜잭션을 쓰므로, 셋업 데이터가 테스트의 암묵적 트랜잭션 안에만 남아있으면(아직
// 커밋 전) 그 별도 트랜잭션에서는 보이지 않는다. 그래서 셋업/검증 모두 TransactionTemplate(REQUIRES_NEW)로
// 실제 커밋을 거쳐야 한다 - UserOptimisticLockTest/PointServiceConcurrencyTest와 동일한 이유.
@DataJpaTest
class PointChargeOrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PointChargeOrderRepository pointChargeOrderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate requiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Test
    @DisplayName("markFailedIfPending: PENDING 주문은 FAILED로 바뀌고 1을 반환한다")
    void markFailedIfPending_pendingOrder_updatesAndReturnsOne() {
        TransactionTemplate requiresNew = requiresNewTemplate();
        Long orderPk = requiresNew.execute(status -> {
            Long userId = persistUser("charge-order-pending@test.com");
            return pointChargeOrderRepository.save(
                    PointChargeOrder.builder().orderId("order-pending").userId(userId).amount(10000).build()).getId();
        });

        int updated = requiresNew.execute(status -> pointChargeOrderRepository.markFailedIfPending("order-pending"));

        assertThat(updated).isEqualTo(1);
        PointChargeOrder found = requiresNew.execute(status -> pointChargeOrderRepository.findById(orderPk).orElseThrow());
        assertThat(found.getStatus()).isEqualTo(PointChargeOrderStatus.FAILED);
    }

    @Test
    @DisplayName("markFailedIfPending: 이미 CONFIRMED인 주문은 바꾸지 않고 0을 반환한다")
    void markFailedIfPending_alreadyConfirmed_returnsZero() {
        TransactionTemplate requiresNew = requiresNewTemplate();
        Long orderPk = requiresNew.execute(status -> {
            Long userId = persistUser("charge-order-confirmed@test.com");
            PointChargeOrder order = PointChargeOrder.builder()
                    .orderId("order-confirmed").userId(userId).amount(10000).build();
            order.markConfirmed();
            return pointChargeOrderRepository.save(order).getId();
        });

        int updated = requiresNew.execute(status -> pointChargeOrderRepository.markFailedIfPending("order-confirmed"));

        assertThat(updated).isZero();
        PointChargeOrder found = requiresNew.execute(status -> pointChargeOrderRepository.findById(orderPk).orElseThrow());
        assertThat(found.getStatus()).isEqualTo(PointChargeOrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("markFailedIfPending: REQUIRES_NEW라 호출자 트랜잭션이 이후 롤백돼도 FAILED 기록은 그대로 남는다")
    void markFailedIfPending_survivesCallerRollback() {
        TransactionTemplate requiresNew = requiresNewTemplate();
        Long orderPk = requiresNew.execute(status -> {
            Long userId = persistUser("charge-order-rollback@test.com");
            return pointChargeOrderRepository.save(
                    PointChargeOrder.builder().orderId("order-rollback").userId(userId).amount(10000).build()).getId();
        });

        // 실제 PointChargeService.confirm()과 동일한 상황을 재현한다: 바깥 트랜잭션 안에서
        // markFailedIfPending()을 호출한 뒤, 그 바깥 트랜잭션 자체는 결국 롤백된다.
        requiresNew.executeWithoutResult(status -> {
            int updated = pointChargeOrderRepository.markFailedIfPending("order-rollback");
            assertThat(updated).isEqualTo(1);
            status.setRollbackOnly();
        });

        PointChargeOrder found = requiresNew.execute(status -> pointChargeOrderRepository.findById(orderPk).orElseThrow());
        assertThat(found.getStatus()).isEqualTo(PointChargeOrderStatus.FAILED);
    }

    private Long persistUser(String email) {
        User user = User.createLocalUser(email, "hashed", email.substring(0, email.indexOf('@')));
        entityManager.persist(user);
        return user.getId();
    }
}
