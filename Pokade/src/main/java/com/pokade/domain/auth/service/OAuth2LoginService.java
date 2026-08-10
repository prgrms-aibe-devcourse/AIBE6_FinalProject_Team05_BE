package com.pokade.domain.auth.service;

import com.pokade.domain.auth.dto.OAuth2Outcome;
import com.pokade.domain.auth.dto.TokenPair;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuth2LoginService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    private static final Duration SIGNUP_TICKET_TTL = Duration.ofMinutes(5);
    private static final String CLAIM_PROVIDER = "provider";
    private static final String CLAIM_EMAIL = "email";

    // OAUTH2 콜백에서 email, provider로 로그인/충돌/신규를 판정한다.
    @Transactional(readOnly = true)
    public OAuth2Outcome resolve(String email, Provider provider) {
        User user = userRepository.findByEmail(email).orElse(null);

        // 신규 -> 유저 안 만들고 가입 티켓 발급
        if (user == null) {
            String ticket = jwtTokenProvider.createSignedTicket(
                    Map.of(CLAIM_PROVIDER, provider.name(), CLAIM_EMAIL, email),
                    SIGNUP_TICKET_TTL);
            return new OAuth2Outcome.SignupRequired(ticket);
        }

        // email이 다른 provider로 존재 -> 충돌
        if (user.getProvider() != provider) {
            return new OAuth2Outcome.Conflict(user.getProvider());
        }

        switch (user.getStatus()) {
            case SUSPENDED -> throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            case PENDING, DELETED -> throw new BusinessException(ErrorCode.LOGIN_FAILED);
            case ACTIVE, WITHDRAWAL_PENDING -> {/* 로그인 허용*/}
        }
        TokenPair tokens = authService.issueToken(user);
        return new OAuth2Outcome.LoggedIn(tokens.refreshToken());
    }

    // 서명 티켓을 검증하고 신규 소셜 유저를 생성한 뒤 토큰을 발급한다.
    @Transactional
    public TokenPair register(String ticket, String nickname) {
        String providerName = jwtTokenProvider.parseSignedTicket(ticket, CLAIM_PROVIDER);
        String email = jwtTokenProvider.parseSignedTicket(ticket, CLAIM_EMAIL);

        if (providerName == null || email == null) {   // 위조·만료 티켓
            throw new BusinessException(ErrorCode.INVALID_OAUTH2_TICKET);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = userRepository.save(
                User.createSocialUser(email, nickname, Provider.valueOf(providerName)));
        return authService.issueToken(user);
    }
}