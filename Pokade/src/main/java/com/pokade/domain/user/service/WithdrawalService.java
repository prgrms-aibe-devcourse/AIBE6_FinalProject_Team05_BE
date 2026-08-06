package com.pokade.domain.user.service;

import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.event.UserWithdrawalCancelledEvent;
import com.pokade.global.event.UserWithdrawalRequestedEvent;
import com.pokade.global.event.UserWithdrawnEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.TokenBlacklistStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklistStore tokenBlacklistStore;

    private static final int GRACE_PERIOD_DAYS = 7;

    // 탈퇴 신청한다 (ACTIVE + 비밀번호 확인 후 유예 상태로 전환, 이벤트 발행)
    @Transactional
    public void requestWithdrawal(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
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
    @Transactional
    public void confirmExpiredWithdrawals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(GRACE_PERIOD_DAYS);

        List<User> targets = userRepository.findAllByStatusAndWithdrawalRequestedAtBefore(
                UserStatus.WITHDRAWAL_PENDING, cutoff);

        for (User user : targets) {
            user.confirmWithdrawal(now);
            refreshTokenStore.delete(user.getId());
            tokenBlacklistStore.blacklist(user.getId());
            eventPublisher.publishEvent(new UserWithdrawnEvent(user.getId()));
        }
    }
}
