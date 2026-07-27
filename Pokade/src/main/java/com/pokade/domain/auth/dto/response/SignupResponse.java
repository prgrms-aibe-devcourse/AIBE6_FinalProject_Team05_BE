package com.pokade.domain.auth.dto.response;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;

public record SignupResponse(
        Long userId,
        String email,
        String nickname,
        UserStatus status
) {
    public static SignupResponse from (User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getStatus()
        );
    }
}
