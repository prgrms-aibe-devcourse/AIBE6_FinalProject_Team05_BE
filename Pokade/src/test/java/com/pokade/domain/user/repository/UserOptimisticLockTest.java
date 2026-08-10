package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User 낙관적 락(@Version) 검증.
 * 확정 배치(WithdrawalService.confirmExpiredWithdrawals)와 유저 철회(cancelWithdrawal)가
 * 같은 행을 동시에 건드릴 때 발생하는 갱신 유실(lost update)을 @Version이 막는지 확인한다.
 * 실제 두 트랜잭션 경합을 재현하려면 커밋된 데이터가 필요하므로 REQUIRES_NEW로 별도 트랜잭션을 쓴다.
 */
@DataJpaTest
class UserOptimisticLockTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("확정 배치가 그새 철회(ACTIVE)된 유저를 낡은 스냅샷으로 덮어쓰려 하면 OptimisticLockException으로 막힌다")
    void 철회와_확정_경합_시_낙관적_락이_갱신유실을_막는다() {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 유예중(WITHDRAWAL_PENDING) 유저를 커밋해 둔다. version = 0
        Long userId = requiresNew.execute(status -> {
            User user = pendingUser("optlock-race@pokade.test", "옵락경합");
            entityManager.persist(user);
            entityManager.flush();
            return user.getId();
        });

        try {
            // 1) 확정 배치가 읽은 시점의 "낡은 스냅샷"(version=0)을 detach 해서 트랜잭션 밖으로 들고 나온다
            User staleBatchView = requiresNew.execute(status -> {
                User loaded = entityManager.find(User.class, userId);
                entityManager.detach(loaded);
                return loaded;
            });
            assertThat(staleBatchView.getVersion()).isZero();

            // 2) 그 사이 유저가 철회 → ACTIVE 로 커밋된다. DB version 0 -> 1
            requiresNew.executeWithoutResult(status -> {
                User loaded = entityManager.find(User.class, userId);
                loaded.cancelWithdrawal();
                entityManager.flush();
            });

            // 3) 배치가 낡은 스냅샷(version=0)으로 확정(DELETED+익명화)을 시도 → version 불일치로 거부된다
            staleBatchView.confirmWithdrawal(LocalDateTime.now(), "anontoken12");
            assertThatThrownBy(() ->
                    requiresNew.executeWithoutResult(status -> userRepository.saveAndFlush(staleBatchView))
            ).isInstanceOf(ObjectOptimisticLockingFailureException.class);

            // 4) 철회가 유지된다 — 계정이 삭제/익명화되지 않았음을 확인
            User after = requiresNew.execute(status -> entityManager.find(User.class, userId));
            assertThat(after.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(after.getEmail()).doesNotStartWith("deleted_");
        } finally {
            requiresNew.executeWithoutResult(status -> {
                User u = entityManager.find(User.class, userId);
                if (u != null) {
                    entityManager.remove(u);
                }
            });
        }
    }

    @Test
    @DisplayName("경합이 없으면 확정이 정상 처리되고 version이 1 증가한다")
    void 경합이_없으면_확정이_정상처리되고_version이_증가한다() {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long userId = requiresNew.execute(status -> {
            User user = pendingUser("optlock-happy@pokade.test", "옵락정상");
            entityManager.persist(user);
            entityManager.flush();
            return user.getId();
        });

        try {
            // 확정을 dirty-checking으로 커밋 (배치와 동일한 경로)
            requiresNew.executeWithoutResult(status -> {
                User loaded = entityManager.find(User.class, userId);
                loaded.confirmWithdrawal(LocalDateTime.now(), "anontoken12");
                entityManager.flush();
            });

            User after = requiresNew.execute(status -> entityManager.find(User.class, userId));
            assertThat(after.getStatus()).isEqualTo(UserStatus.DELETED);
            assertThat(after.getVersion()).isEqualTo(1L);
            assertThat(after.getEmail()).startsWith("deleted_");
        } finally {
            requiresNew.executeWithoutResult(status -> {
                User u = entityManager.find(User.class, userId);
                if (u != null) {
                    entityManager.remove(u);
                }
            });
        }
    }

    // 유예중(WITHDRAWAL_PENDING) 유저 — ACTIVE로 만든 뒤 도메인 메서드로 상태 전이
    private User pendingUser(String email, String nickname) {
        User u = User.builder()
                .email(email).password("ENCODED_PW")
                .nickname(nickname).role(Role.USER).provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .pointBalance(0)
                .build();
        u.requestWithdrawal(LocalDateTime.now().minusDays(8));
        return u;
    }
}
