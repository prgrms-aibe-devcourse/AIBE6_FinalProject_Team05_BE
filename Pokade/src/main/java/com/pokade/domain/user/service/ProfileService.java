package com.pokade.domain.user.service;

import com.pokade.domain.user.dto.response.MyProfileResponse;
import com.pokade.domain.user.dto.response.PublicProfileResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.ListingCountPort;
import com.pokade.global.port.TradeCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository userRepository;
    private final TradeCountPort tradeCountPort;
    private final ListingCountPort listingCountPort;

    // 공개 프로필을 조회한다 ( 확정 탈퇴 계정은 없는 것을 전제로 한다)
    public PublicProfileResponse getPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return PublicProfileResponse.of(
                user,
                tradeCountPort.countCompletedTrades(userId),
                listingCountPort.countActiveListings(userId)
        );
    }

    // 본인 상세 프로필을 조회한다

    public MyProfileResponse getMyProfile(Long userId) {
        return userRepository.findById(userId)
                .map(MyProfileResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
