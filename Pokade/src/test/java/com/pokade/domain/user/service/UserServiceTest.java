package com.pokade.domain.user.service;

import com.pokade.domain.user.dto.response.UserResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
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

    private User localUser(String nickname, LocalDateTime nicknameChangedAt) {
        return User.builder()
                .id(1L)
                .email("user@pokade.com")
                .password("ENCODED_OLD")
                .nickname(nickname)
                .nicknameChangedAt(nicknameChangedAt)
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private User socialUser() {
        return User.builder()
                .id(1L)
                .email("kakao@pokade.com")
                .nickname("카카오")
                .role(Role.USER)
                .provider(Provider.KAKAO)
                .status(UserStatus.ACTIVE)
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

    @Test
    @DisplayName("닉네임 변경 성공 시 닉네임과 변경 시각이 갱신된다")
    void updateNickname_success() {
        User user = localUser("지우", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("리코")).willReturn(false);

        userService.updateNickname(1L, "리코");

        assertThat(user.getNickname()).isEqualTo("리코");
        assertThat(user.getNicknameChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("현재 닉네임과 동일하면 중복검사 없이 통과한다")
    void updateNickname_sameNickname_noOp() {
        User user = localUser("지우", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.updateNickname(1L, "지우");

        then(userRepository).should(never()).existsByNickname(any());
        assertThat(user.getNickname()).isEqualTo("지우");
    }

    @Test
    @DisplayName("닉네임 변경 쿨다운(30일) 중이면 NICKNAME_CHANGE_LIMITED 예외를 던진다")
    void updateNickname_cooldown() {
        User user = localUser("지우", LocalDateTime.now().minusDays(5));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateNickname(1L, "리코"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_CHANGE_LIMITED);

        assertThat(user.getNickname()).isEqualTo("지우");
    }

    @Test
    @DisplayName("변경할 닉네임이 이미 사용 중이면 DUPLICATE_NICKNAME 예외를 던진다")
    void updateNickname_duplicate() {
        User user = localUser("지우", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("리코")).willReturn(true);

        assertThatThrownBy(() -> userService.updateNickname(1L, "리코"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        assertThat(user.getNickname()).isEqualTo("지우");
    }

    @Test
    @DisplayName("닉네임 변경 시 유저가 없으면 USER_NOT_FOUND 예외를 던진다")
    void updateNickname_userNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateNickname(999L, "리코"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("동시 요청 경합으로 DB 유니크 제약을 위반하면 DUPLICATE_NICKNAME 예외를 던진다")
    void updateNickname_uniqueViolation() {
        User user = localUser("지우", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("리코")).willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_users_nickname"))
                .given(userRepository).flush();

        assertThatThrownBy(() -> userService.updateNickname(1L, "리코"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하면 새 비밀번호로 변경한다")
    void changePassword_success() {
        User user = localUser("지우", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("curPw1234", "ENCODED_OLD")).willReturn(true);
        given(passwordEncoder.encode("newPw1234")).willReturn("ENCODED_NEW");

        userService.changePassword(1L, "curPw1234", "newPw1234");

        assertThat(user.getPassword()).isEqualTo("ENCODED_NEW");
    }

    @Test
    @DisplayName("소셜 로그인 계정이면 PASSWORD_CHANGE_NOT_ALLOWED 예외를 던진다")
    void changePassword_socialUser() {
        given(userRepository.findById(1L)).willReturn(Optional.of(socialUser()));

        assertThatThrownBy(() -> userService.changePassword(1L, "curPw1234", "newPw1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED);

        then(passwordEncoder).should(never()).matches(any(), any());
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 INVALID_CURRENT_PASSWORD 예외를 던진다")
    void changePassword_wrongCurrent() {
        User user = localUser("지우", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPw", "ENCODED_OLD")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, "wrongPw", "newPw1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);

        assertThat(user.getPassword()).isEqualTo("ENCODED_OLD");
    }

    @Test
    @DisplayName("비밀번호 변경 시 유저가 없으면 USER_NOT_FOUND 예외를 던진다")
    void changePassword_userNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(999L, "curPw1234", "newPw1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
