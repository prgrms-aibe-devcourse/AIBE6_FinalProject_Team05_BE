package com.pokade.domain.user.service;

import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.event.UserWithdrawalCancelledEvent;
import com.pokade.global.event.UserWithdrawalRequestedEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.TokenBlacklistStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklistStore tokenBlacklistStore;
    private final WithdrawalConfirmer withdrawalConfirmer;

    private static final int GRACE_PERIOD_DAYS = 7;
    private static final int MAX_ANON_RETRY = 3;


    // 탈퇴 신청한다 (ACTIVE + 비밀번호 확인 후 유예 상태로 전환, 이벤트 발행)
    @Transactional
    public void requestWithdrawal(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }

        if (user.isLocalUser()) {
            if (password == null || password.isBlank()
                    || !passwordEncoder.matches(password, user.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
            }
        }

        user.requestWithdrawal(LocalDateTime.now());
        eventPublisher.publishEvent(new UserWithdrawalRequestedEvent(userId));
    }

    // 탈퇴 신청을 철회한다(유예 상태에서만, 활성 복구 + 이벤트 발생)
    @Transactional
    public void cancelWithdrawal(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            throw new BusinessException(ErrorCode.NOT_WITHDRAWAL_PENDING);
        }

        user.cancelWithdrawal();
        eventPublisher.publishEvent(new UserWithdrawalCancelledEvent(userId));
    }

    // 유예(7일) 지난 탈퇴 신청을 확정 = soft-delete·익명화·토큰 무효화·이벤트 발행
    @Scheduled(cron = "0 0 4 * * *")
    public void confirmExpiredWithdrawals() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS);

        List<Long> targetIds = userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(UserStatus.WITHDRAWAL_PENDING, cutoff)
                .stream()
                .map(User::getId)
                .toList();
        for (Long userId : targetIds) {
            boolean confirmed = false;
            int attempts = 0;
            while (true) {
                try {
                    confirmed = withdrawalConfirmer.confirm(userId); // 매 호출 새 토큰 + 새 트랜잭션
                    break;
                } catch (DataIntegrityViolationException e) {         // 익명화 토큰 UNIQUE 충돌 → 새 토큰으로 재시도
                    if (++attempts >= MAX_ANON_RETRY) {
                        log.error("탈퇴 확정 실패 - 익명화 토큰 충돌 재시도 초과 (userId={})", userId, e);
                        break; // confirmed=false 유지, 남으면 다음 배치가 또 재시도(자가치유)
                    }
                } catch (Exception e) {
                    log.error("탈퇴 확정 실패 - 다음 대상 계속 진행 (userId={})", userId, e);
                    break;
                }
            }
            if (!confirmed) {
                continue; // 확정 안 됨(그새 철회/이미 확정 or 재시도 초과) → 토큰 정리 불필요
            }
            try {
                refreshTokenStore.delete(userId); // refresh token 삭제
                tokenBlacklistStore.blacklist(userId); // access token blacklist 처리
            } catch (Exception e) {
                // 확정(DB)은 됐으나 토큰 정리 실패 → 운영 보정 대상(기존 access가 만료 전까지 유효)
                log.error("탈퇴 확정됨(DB) - 토큰 정리 실패, 보정 필요 (userId={})", userId, e);
            }
        }
    }
}
