package com.pokade.domain.user.dto.response;

import com.pokade.domain.user.entity.User;

import java.time.LocalDateTime;

public record PublicProfileResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDateTime joinedAt,
        long completedTradeCount,
        long activeListingCount
) {
    public static PublicProfileResponse of(User user, long completedTradeCount, long activeListingCount) {
        return new PublicProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getCreated_At(),
                completedTradeCount,
                activeListingCount
        );
    }
}
