package com.pokade.domain.user.support;

import com.pokade.domain.user.entity.User;

// 프로필 이미지 응답 경로 규칙을 한곳에 모은다 (응답에 내부 S3 key를 싣지 않기 위함).
public final class ProfileImagePath {

    private ProfileImagePath() {
    }

    // 이미지가 없으면 null, 있으면 프록시 조회 경로를 반환한다 (ProfileImageController의 매핑과 짝을 이룬다).
    public static String of(User user) {
        return user.getProfileImageUrl() == null ? null : "/api/users/" + user.getId() + "/profile/image";
    }
}