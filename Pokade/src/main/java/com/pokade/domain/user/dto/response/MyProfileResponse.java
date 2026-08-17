package com.pokade.domain.user.dto.response;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MyProfileResponse(
        String email,
        String phoneNumber,
        Provider provider,
        boolean socialLinked,
        LocalDateTime joinedAt,
        LocalDate birthDate
) {
    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(
                user.getEmail(),
                user.getPhoneNumber(),
                user.getProvider(),
                !user.isLocalUser(),
                user.getCreated_At(),
                user.getBirthDate()
        );
    }
}
