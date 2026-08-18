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
                toImagePath(user),
                user.getCreated_At(),
                completedTradeCount,
                activeListingCount
        );
    }

    // 내부 S3 key 대신 프록시 조회 경로를 내려준다 (이미지가 없으면 null)
    private static String toImagePath(User user) {
        return user.getProfileImageUrl() == null ? null : "/api/users/" + user.getId() + "/profile/image";
    }
}
