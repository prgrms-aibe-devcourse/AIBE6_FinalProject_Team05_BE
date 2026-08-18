package com.pokade.domain.user.service;

import com.pokade.domain.user.dto.response.MyProfileResponse;
import com.pokade.domain.user.dto.response.PublicProfileResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.ListingCountPort;
import com.pokade.global.port.TradeCountPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TradeCountPort tradeCountPort;
    @Mock
    private ListingCountPort listingCountPort;

    @InjectMocks
    private ProfileService profileService;

    private User userWith(UserStatus status) {
        return User.builder()
                .id(1L).email("user@pokade.com").password("ENCODED_PW")
                .nickname("지우").role(Role.USER).provider(Provider.LOCAL)
                .status(status).pointBalance(0)
                .build();
    }

    // ===== 공개 프로필 =====

    @Test
    @DisplayName("공개 프로필: 거래 수와 판매 중 매물 수를 함께 반환한다")
    void getPublicProfile_success() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWith(UserStatus.ACTIVE)));
        given(tradeCountPort.countCompletedTrades(1L)).willReturn(3L);
        given(listingCountPort.countActiveListings(1L)).willReturn(2L);

        PublicProfileResponse res = profileService.getPublicProfile(1L);

        assertThat(res.userId()).isEqualTo(1L);
        assertThat(res.nickname()).isEqualTo("지우");
        assertThat(res.completedTradeCount()).isEqualTo(3L);
        assertThat(res.activeListingCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("공개 프로필: 존재하지 않는 userId면 USER_NOT_FOUND")
    void getPublicProfile_notFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getPublicProfile(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("공개 프로필: 확정 탈퇴 계정은 존재하지 않는 것으로 취급한다")
    void getPublicProfile_deletedTreatedAsNotFound() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWith(UserStatus.DELETED)));

        assertThatThrownBy(() -> profileService.getPublicProfile(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("공개 프로필: 탈퇴 유예 중인 계정은 정상 노출된다")
    void getPublicProfile_withdrawalPendingIsVisible() {
        User user = userWith(UserStatus.ACTIVE);
        user.requestWithdrawal(LocalDateTime.now()); // ACTIVE -> WITHDRAWAL_PENDING
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(tradeCountPort.countCompletedTrades(1L)).willReturn(1L);
        given(listingCountPort.countActiveListings(1L)).willReturn(0L);

        PublicProfileResponse res = profileService.getPublicProfile(1L);

        assertThat(res.nickname()).isEqualTo("지우");
        assertThat(res.completedTradeCount()).isEqualTo(1L);
    }

    // ===== 내 프로필 상세 =====

    @Test
    @DisplayName("내 프로필: 로컬 계정이면 socialLinked 가 false")
    void getMyProfile_localAccount() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWith(UserStatus.ACTIVE)));

        MyProfileResponse res = profileService.getMyProfile(1L);

        assertThat(res.email()).isEqualTo("user@pokade.com");
        assertThat(res.provider()).isEqualTo(Provider.LOCAL);
        assertThat(res.socialLinked()).isFalse();
    }

    @Test
    @DisplayName("내 프로필: 소셜 계정이면 socialLinked 가 true")
    void getMyProfile_socialAccount() {
        User user = User.builder()
                .id(1L).email("user@pokade.com")
                .nickname("지우").role(Role.USER).provider(Provider.GOOGLE)
                .status(UserStatus.ACTIVE).pointBalance(0)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyProfileResponse res = profileService.getMyProfile(1L);

        assertThat(res.provider()).isEqualTo(Provider.GOOGLE);
        assertThat(res.socialLinked()).isTrue();
    }

    @Test
    @DisplayName("내 프로필: 존재하지 않는 userId면 USER_NOT_FOUND")
    void getMyProfile_notFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getMyProfile(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
