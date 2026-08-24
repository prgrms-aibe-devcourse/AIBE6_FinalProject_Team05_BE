package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// #392: 관리자 대상 알림(INQUIRY_RECEIVED) 팬아웃의 수신자 조회 계약을 고정한다.
// 이 쿼리가 role만 보고 status를 빠뜨리면 탈퇴/삭제된 관리자 계정에도 알림 행이 계속 쌓인다 -
// 그래서 "ADMIN이면서 ACTIVE"라는 두 조건이 모두 걸리는지가 이 테스트의 핵심이다.
//
// User.createLocalUser()는 role을 USER로 고정하므로, 관리자 픽스처는 @Builder로 직접 만든다.
@DataJpaTest
class UserRepositoryAdminLookupTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User persist(String email, String nickname, Role role, UserStatus status) {
        User user = User.builder()
                .email(email)
                .password("hashed")
                .nickname(nickname)
                .provider(Provider.LOCAL)
                .role(role)
                .status(status)
                .pointBalance(0)
                .build();
        entityManager.persist(user);
        return user;
    }

    @Test
    @DisplayName("ADMIN + ACTIVE 유저만 반환하고 일반 유저는 제외한다")
    void findByRoleAndStatus_returnsOnlyActiveAdmins() {
        User activeAdmin = persist("admin-a@test.com", "adminA", Role.ADMIN, UserStatus.ACTIVE);
        User anotherActiveAdmin = persist("admin-b@test.com", "adminB", Role.ADMIN, UserStatus.ACTIVE);
        persist("plain-user@test.com", "plainUser", Role.USER, UserStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        List<User> admins = userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);

        assertThat(admins).extracting(User::getId)
                .containsExactlyInAnyOrder(activeAdmin.getId(), anotherActiveAdmin.getId());
    }

    @Test
    @DisplayName("ACTIVE가 아닌 관리자(정지·탈퇴대기·삭제)는 알림 대상에서 제외된다")
    void findByRoleAndStatus_excludesNonActiveAdmins() {
        User activeAdmin = persist("admin-active@test.com", "adminActive", Role.ADMIN, UserStatus.ACTIVE);
        persist("admin-suspended@test.com", "adminSuspended", Role.ADMIN, UserStatus.SUSPENDED);
        persist("admin-pending@test.com", "adminPending", Role.ADMIN, UserStatus.WITHDRAWAL_PENDING);
        persist("admin-deleted@test.com", "adminDeleted", Role.ADMIN, UserStatus.DELETED);
        entityManager.flush();
        entityManager.clear();

        List<User> admins = userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);

        assertThat(admins).extracting(User::getId).containsExactly(activeAdmin.getId());
    }

    @Test
    @DisplayName("활성 관리자가 한 명도 없으면 빈 목록을 반환한다(예외가 아니다)")
    void findByRoleAndStatus_withNoAdmin_returnsEmptyList() {
        persist("only-user@test.com", "onlyUser", Role.USER, UserStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).isEmpty();
    }
}
