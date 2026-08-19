package com.pokade.domain.admin.service;

import com.pokade.domain.admin.dto.response.AdminUserResponse;
import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.domain.user.service.WithdrawalConfirmer;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final WithdrawalConfirmer withdrawalConfirmer;

    // 회원 목록 조회 - 선택 필터는 여기서 null이 아닌 값으로 정규화한다.
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(UserStatus status, Role role, String keyword, Pageable pageable) {
        List<UserStatus> statuses = status != null ? List.of(status) : Arrays.asList(UserStatus.values());
        List<Role> roles = role != null ? List.of(role) : Arrays.asList(Role.values());
        String normalizedKeyword = keyword != null ? keyword.trim() : "";

        return userRepository.findForAdmin(statuses, roles, normalizedKeyword, pageable)
                .map(AdminUserResponse::from);
    }

    // 계정 정지 - 로그인,재발급은 이미 상태로 막히지만, 발급된 refresh를 지위 재발급 경로를 즉시 닫는다
    @Transactional
    public void suspend(Long adminId, Long targetId) {
        User target = findTarget(adminId, targetId);
        if (target.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ALREADY_SUSPENDED);
        }
        if (target.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.ALREADY_WITHDRAWN);
        }
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SUSPEND_NOT_ALLOWED);
        }
        target.suspend();
        refreshTokenStore.deleteAll(targetId);
    }

    // 정지 해제
    @Transactional
    public void unsuspend(Long adminId, Long targetId) {
        User target = findTarget(adminId, targetId);
        if (target.getStatus() != UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.NOT_SUSPENDED);
        }
        target.unsuspend();
    }

    // 강제 탈퇴 - 유예 없이 즉시 확정한다. 유예 상태로 만든 뒤 같은 트랜잭션에서 확정 로직을 그대로 재사용한다.
    @Transactional
    public void forceWithdraw(Long adminId, Long targetId) {
        User target = findTarget(adminId, targetId);
        if (target.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.ALREADY_WITHDRAWN);
        }
        target.requestWithdrawal(LocalDateTime.now());
        userRepository.flush(); // confirm이 다시 조회하므로 상태 전이를 먼저 반영한다.
        withdrawalConfirmer.confirm(targetId);
    }

    private User findTarget(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new BusinessException(ErrorCode.ADMIN_CANNOT_TARGET_SELF);
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (target.getRole() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_CANNOT_TARGET_ADMIN);
        }
        return target;
    }
}
