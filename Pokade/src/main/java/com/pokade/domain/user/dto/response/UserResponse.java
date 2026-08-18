package com.pokade.domain.user.dto.response;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.support.ProfileImagePath;

import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String email,
        String nickname,
        Role role,
        UserStatus status,
        String profileImageUrl,
        Integer pointBalance,
        Provider provider,
        LocalDateTime withdrawalRequestedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                ProfileImagePath.of(user),
                user.getPointBalance(),
                user.getProvider(),
                user.getWithdrawalRequestedAt()
        );
    }
}
