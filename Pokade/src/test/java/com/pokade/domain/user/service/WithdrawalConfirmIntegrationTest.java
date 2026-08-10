package com.pokade.domain.user.service;

import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.domain.user.support.AnonymizationTokenGenerator;
import com.pokade.global.security.TokenBlacklistStore;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 회원 탈퇴 확정 배치를 "실제 Spring 빈 배선 + 실제 DB + 실제 쿼리"로 검증한다.
 * 단위 테스트가 목으로 가렸던 부분(빈 주입/트랜잭션 프록시/파생 쿼리/실제 UPDATE·version)을 확인한다.
 * Redis 스토어(RefreshTokenStore·TokenBlacklistStore)만 목으로 두고 호출 여부만 본다.
 */
@DataJpaTest
@Import({WithdrawalService.class, WithdrawalConfirmer.class, AnonymizationTokenGenerator.class})
class WithdrawalConfirmIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager em;

    @MockitoBean
    private PasswordEncoder passwordEncoder;        // WithdrawalService 생성자 의존만 충족(이 테스트에선 미사용)
    @MockitoBean
    private RefreshTokenStore refreshTokenStore;    // Redis 없이 — 호출 여부만 검증
    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @Test
    @DisplayName("실제 빈·DB: 유예 만료 유저를 배치가 DELETED 익명화하고 version을 올리며 토큰 정리를 호출한다")
    void confirmExpired_realBeansAndDb() {
        Long id = persistPending("e2e-expired@pokade.test", "만료대상", 8);

        // 실제 WithdrawalService → WithdrawalConfirmer 배선을 그대로 실행
        withdrawalService.confirmExpiredWithdrawals();

        em.flush();
        em.clear();
        User after = userRepository.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(after.getEmail()).matches("deleted_[0-9a-f]{12}@pokade\\.invalid");
        assertThat(after.getNickname()).matches("deleted_[0-9a-f]{12}");
        assertThat(after.getPassword()).isNull();
        assertThat(after.getVersion()).isEqualTo(1L); // 실제 DB 컬럼으로 낙관적 락 버전 증가
        then(refreshTokenStore).should().delete(id);
        then(tokenBlacklistStore).should().blacklist(id);
    }

    @Test
    @DisplayName("실제 파생 쿼리: 유예 미경과(3일) 유저는 배치 대상에서 제외되어 그대로 유지된다")
    void confirmExpired_gracePeriodBoundary() {
        Long expiredId = persistPending("e2e-exp@pokade.test", "만료", 8);   // 8일 전 신청 → 대상
        Long freshId = persistPending("e2e-fresh@pokade.test", "신선", 3);   // 3일 전 신청 → 유예 중

        withdrawalService.confirmExpiredWithdrawals();

        em.flush();
        em.clear();
        assertThat(userRepository.findById(expiredId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.DELETED);
        assertThat(userRepository.findById(freshId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        then(refreshTokenStore).should().delete(expiredId);
        then(refreshTokenStore).should(never()).delete(freshId);
    }

    // ACTIVE로 만든 뒤 도메인 메서드로 유예 전환 (requestedDaysAgo일 전 신청으로 기록)
    private Long persistPending(String email, String nickname, int requestedDaysAgo) {
        User u = User.builder()
                .email(email).password("ENCODED_PW")
                .nickname(nickname).role(Role.USER).provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .pointBalance(0)
                .build();
        u.requestWithdrawal(LocalDateTime.now().minusDays(requestedDaysAgo));
        userRepository.save(u);
        em.flush();
        return u.getId();
    }
}
