package com.pokade.domain.user.dto.response;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.support.ProfileImagePath;

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
                ProfileImagePath.of(user),
                user.getCreated_At(),
                completedTradeCount,
                activeListingCount
        );
    }

}
