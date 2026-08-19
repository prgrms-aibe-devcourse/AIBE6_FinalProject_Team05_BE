package com.pokade.domain.user.support;

import com.pokade.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImagePathTest {

    private User userWith(String profileImageKey) {
        return User.builder()
                .id(7L)
                .email("user@pokade.com")
                .nickname("지우")
                .profileImageUrl(profileImageKey)
                .build();
    }

    @Test
    @DisplayName("이미지가 있으면 프록시 조회 경로를 반환한다")
    void of_withImage() {
        assertThat(ProfileImagePath.of(userWith("profile/9f3c2a.png")))
                .isEqualTo("/api/users/7/profile/image");
    }

    @Test
    @DisplayName("반환 경로에 내부 S3 key가 섞이지 않는다")
    void of_doesNotExposeKey() {
        assertThat(ProfileImagePath.of(userWith("profile/9f3c2a.png")))
                .doesNotContain("9f3c2a");
    }

    @Test
    @DisplayName("이미지가 없으면 null을 반환한다")
    void of_withoutImage() {
        assertThat(ProfileImagePath.of(userWith(null))).isNull();
    }
}
