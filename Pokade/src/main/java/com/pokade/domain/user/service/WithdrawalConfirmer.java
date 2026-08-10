package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.event.UserWithdrawnEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WithdrawalConfirmer {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 유저 한 명의 탈퇴를 확정한다 (건별 독립 트랜잭션). 실제 확정하면 true, 그새 철회/확정돼 건너뛰면 false
    @Transactional
    public boolean confirm(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            return false; // 멱등 skip - 그새 철회(ACTIVE)됐거나 이미 확정 (DELETED)
        }
        String anonToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user.confirmWithdrawal(LocalDateTime.now(), anonToken);
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
        return true;
    }
}
