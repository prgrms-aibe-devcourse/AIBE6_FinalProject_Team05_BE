package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.User;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// findByIdWithLock()의 "정상 조회" 계약만 검증한다. 실제 비관적 락이 동시 트랜잭션을 차단하는지는
// PointService 도입 시 그 서비스를 대상으로 한 동시성 테스트에서 함께 검증한다.
@DataJpaTest
class UserRepositoryLockTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("존재하는 유저 id로 조회하면 해당 유저를 반환한다")
    void findByIdWithLock_existingUser_returnsUser() {
        User user = User.createLocalUser("lock-user@test.com", "hashed", "tester");
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        Optional<User> found = userRepository.findByIdWithLock(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("lock-user@test.com");
    }

    @Test
    @DisplayName("존재하지 않는 유저 id로 조회하면 빈 Optional을 반환한다")
    void findByIdWithLock_missingUser_returnsEmpty() {
        Optional<User> found = userRepository.findByIdWithLock(999_999_999L);

        assertThat(found).isEmpty();
    }
}
