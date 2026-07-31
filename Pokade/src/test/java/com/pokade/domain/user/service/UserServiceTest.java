package com.pokade.domain.user.service;

import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @InjectMocks
    UserService userService;

    private User user() {
        return User.builder()
                .id(1L)
                .email("user@pokade.com")
                .password("ENCODED_PW")
                .nickname("지우")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .profileImageUrl("https://img/x.png")
                .pointBalance(30)
                .build();
    }

    @Test
    @DisplayName("내 정보 조회 시 프로필 필드를 담은 UserResponse를 반환한다")
    void getMyInfo_success() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));

        UserResponse res = userService.getMyInfo(1L);

        assertThat(res.userId()).isEqualTo(1L);
        assertThat(res.email()).isEqualTo("user@pokade.com");
        assertThat(res.nickname()).isEqualTo("지우");
        assertThat(res.role()).isEqualTo(Role.USER);
        assertThat(res.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(res.profileImageUrl()).isEqualTo("https://img/x.png");
        assertThat(res.pointBalance()).isEqualTo(30);
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 USER_NOT_FOUND 예외를 던진다")
    void getMyInfo_userNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}