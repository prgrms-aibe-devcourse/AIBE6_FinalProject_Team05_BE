package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.auth.store.LoginAttemptStore;
import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
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
    private final LoginAttemptStore loginAttemptStore;

    private String dummyHash;


    @Transactional
    public SignupResponse signup(SignupRequest request) {
        User existing = userRepository.findByEmail(request.email()).orElse(null);
        if (existing != null) {
            throw new BusinessException(existing.getStatus() != UserStatus.ACTIVE
                    ? ErrorCode.EMAIL_NOT_VERIFIED : ErrorCode.DUPLICATE_EMAIL);
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
        String email = request.email();
        if (loginAttemptStore.isBlocked(email)) { // BCrypt 전에 차단 → DoS 증폭 컷
            throw new BusinessException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash); //타이밍 방어
            loginAttemptStore.recordFailure(email);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptStore.recordFailure(email);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        switch (user.getStatus()) {
            case PENDING -> throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
            case SUSPENDED -> throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            case DELETED -> throw new BusinessException(ErrorCode.LOGIN_FAILED);
            case ACTIVE, WITHDRAWAL_PENDING -> {
                // 로그인 허용
            }
        }

        loginAttemptStore.reset(email); // 로그인 성공 시 실패 기록 초기화

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

        if (!refreshTokenStore.exists(userId)) { // 저장 키 자체가 없음 -> 미로그인/로그아웃/만료
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    refreshTokenStore.delete(userId);
                    return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
                });

        // 로그인 가능한 상태 (ACTIVE 유예)만 재발급 허용 - SUSPENDED / DELETED / PENDING 은 저장 refresh 폐기 후 거부
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            refreshTokenStore.delete(userId);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String role = user.getRole().name();
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        if (refreshTokenStore.compareAndRotate(userId, refreshToken, newRefreshToken)) {
            String newAccessToken = jwtTokenProvider.createAccessToken(userId, role);
            return new TokenPair(newAccessToken, newRefreshToken);
        }

        if (refreshTokenStore.matchesGrace(userId, refreshToken)) {
            String accessToken = jwtTokenProvider.createAccessToken(userId, role);
            return new TokenPair(accessToken, null); // Option Y : refresh 세팅 안함
        }

        refreshTokenStore.delete(userId);
        throw new BusinessException(ErrorCode.TOKEN_STOLEN);
    }

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("timing-guard");
    }
}