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

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED); // 미인증은 실패로 안 셈
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

        if (refreshTokenStore.matches(userId, refreshToken)) { // 현재 refresh와 일치 -> 정상 회전
            String newAccessToken = jwtTokenProvider.createAccessToken(userId, findRole(userId));
            String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

            refreshTokenStore.saveGrace(userId, refreshToken);
            refreshTokenStore.save(userId, newRefreshToken);

            return new TokenPair(newAccessToken, newRefreshToken);
        }

       if (refreshTokenStore.matchesGrace(userId, refreshToken)) { // 직전 refresh(동시요청) -> 새 access만
           String accessToken = jwtTokenProvider.createAccessToken(userId, findRole(userId));
           return new TokenPair(accessToken, null); // Option Y: refresh 재발급,재세팅 안 함
       }

       refreshTokenStore.delete(userId); // 키는 있는데 어느 것과도 불일치 -> 탈취 의심, 전면 폐기
       throw new BusinessException(ErrorCode.TOKEN_STOLEN);
    }


    private String findRole(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    refreshTokenStore.delete(userId);
                    return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
                })
                .getRole().name();
    }

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("timing-guard");
    }
}