package com.pokade.domain.user.dto.response;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;

import java.time.LocalDateTime;

public record MyProfileResponse(
        String email,
        Provider provider,
        boolean socialLinked,
        LocalDateTime joinedAt,
        boolean marketingAgreed
) {
    public static MyProfileResponse of(User user, boolean marketingAgreed) {
        return new MyProfileResponse(
                user.getEmail(),
                user.getProvider(),
                !user.isLocalUser(),
                user.getCreated_At(),
                marketingAgreed
        );
    }
}
