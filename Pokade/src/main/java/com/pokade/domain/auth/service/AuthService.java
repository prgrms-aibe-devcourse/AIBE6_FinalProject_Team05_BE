package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.auth.dto.request.LoginRequest;
import com.pokade.domain.auth.dto.request.SignupRequest;
import com.pokade.domain.auth.dto.response.SignupResponse;
import com.pokade.domain.auth.store.LoginAttemptStore;
import com.pokade.domain.auth.store.RefreshTokenStore;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.AgreementType;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.domain.user.service.UserAgreementService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtTokenProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptStore loginAttemptStore;
    private final UserAgreementService userAgreementService;

    private String dummyHash;

    // 재발급 결과 카운터 — reused는 폐기된 refresh가 다시 제출된 것으로 토큰 탈취 정황이다.
    // grace는 동시 요청·네트워크 재시도로 정상 발생하므로 0이 아닌 것이 맞고, 둘을 같은
    // 이름의 태그로 나눠 재발급 대비 비율로 본다.
    private static final String REISSUE_METRIC = "auth.token.reissue";
    private static final String LOGIN_FAILED_METRIC = "auth.login.failure";

    private final MeterRegistry meterRegistry;


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

        userAgreementService.recordSignupAgreements(user.getId(), Map.of(
                AgreementType.TERMS_OF_SERVICE, request.termsOfService(),
                AgreementType.PRIVACY_POLICY, request.privacyPolicy(),
                AgreementType.THIRD_PARTY_SHARING, request.thirdPartySharing(),
                AgreementType.MARKETING, request.marketing()
        ));

        return SignupResponse.from(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        String email = request.email();
        if (loginAttemptStore.isBlocked(email)) { // BCrypt 전에 차단 → DoS 증폭 컷
            countLoginFailure("blocked");
            throw new BusinessException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash); //타이밍 방어
            loginAttemptStore.recordFailure(email);
            countLoginFailure("no_account");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptStore.recordFailure(email);
            countLoginFailure("bad_password");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        switch (user.getStatus()) {
            case PENDING -> {
                countLoginFailure("email_unverified");
                throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
            }
            case SUSPENDED -> {
                countLoginFailure("suspended");
                throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            }
            case DELETED -> {
                countLoginFailure("deleted");
                throw new BusinessException(ErrorCode.LOGIN_FAILED);
            }
            case ACTIVE, WITHDRAWAL_PENDING -> {
                // 로그인 허용
            }
        }

        loginAttemptStore.reset(email); // 로그인 성공 시 실패 기록 초기화

        return issueToken(user);
    }

    public void logout(String refreshToken) {
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String sid = jwtTokenProvider.getSessionId(refreshToken);
        if (userId != null && sid != null) {
            refreshTokenStore.delete(userId, sid);
        }
    }

    public TokenPair reissue(String refreshToken) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            countReissue("invalid");
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String sid = jwtTokenProvider.getSessionId(refreshToken);
        if (sid == null) {
            countReissue("invalid");
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!refreshTokenStore.exists(userId, sid)) { // 그 세션 키 없음 -> 로그아웃/만료
            countReissue("invalid");
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    refreshTokenStore.deleteAll(userId);
                    countReissue("invalid");
                    return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
                });

        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            refreshTokenStore.deleteAll(userId); // 정지·삭제 = 계정 전체 세션 차단
            countReissue("invalid");
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String role = user.getRole().name();
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId, sid); // 같은 sid 유지

        if (refreshTokenStore.compareAndRotate(userId, sid, refreshToken, newRefreshToken)) {
            countReissue("rotated");
            String newAccessToken = jwtTokenProvider.createAccessToken(userId, role);
            return new TokenPair(newAccessToken, newRefreshToken);
        }

        if (refreshTokenStore.matchesGrace(userId, sid, refreshToken)) {
            countReissue("grace");
            String accessToken = jwtTokenProvider.createAccessToken(userId, role);
            return new TokenPair(accessToken, null); // Option Y
        }

        countReissue("stolen");
        refreshTokenStore.delete(userId, sid); // 그 세션만 (다른 기기 생존)
        throw new BusinessException(ErrorCode.TOKEN_STOLEN);
    }

    // 재발급 결과를 지표에 기록한다.
    private void countReissue(String result) {
        Counter.builder(REISSUE_METRIC)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    // 로그인 실패 사유를 지표에 기록한다. 응답 코드는 뭉개도 지표는 사유별로 구분한다.
    private void countLoginFailure(String reason) {
        Counter.builder(LOGIN_FAILED_METRIC)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("timing-guard");
    }

    public TokenPair issueToken(User user) {
        String sid = UUID.randomUUID().toString();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), sid);
        refreshTokenStore.save(user.getId(), sid, refreshToken);
        return new TokenPair(accessToken, refreshToken);
    }
}