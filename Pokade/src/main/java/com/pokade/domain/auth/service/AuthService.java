package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;


    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        User user = userRepository.save(
                User.createLocalUser(request.email(), encodedPassword, request.nickname())
        );

        return SignupResponse.from(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenStore.save(user.getId(), refreshToken);

        return new TokenPair(accessToken, refreshToken);
    }

    public void logout(String accessToken) {
        Long userId = jwtTokenProvider.getUserId(accessToken);
        if (userId != null) {
            refreshTokenStore.delete(userId);
        }
    }

    @Transactional
    public TokenPair reissue(String refreshToken) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String stored = refreshTokenStore.find(userId);

        if (stored == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!stored.equals(refreshToken)) {
            String grace = refreshTokenStore.findGrace(userId);

            if (refreshToken.equals(grace)) {
                String accessToken = jwtTokenProvider.createAccessToken(userId, findRole(userId));
                return new TokenPair(accessToken, stored);
            }
            refreshTokenStore.delete(userId);
            throw new BusinessException(ErrorCode.TOKEN_STOLEN);
        }
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, findRole(userId));
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenStore.saveGrace(userId, refreshToken);
        refreshTokenStore.save(userId, newRefreshToken);

        return new TokenPair(newAccessToken, newRefreshToken);
    }


    private String findRole(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    refreshTokenStore.delete(userId);
                    return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
                })
                .getRole().name();
    }
}