package com.pokade.domain.auth.dto;

import com.pokade.domain.user.entity.type.Provider;

public sealed interface OAuth2Outcome {

    // 기존 소셜 계정 -> 로그인 (refresh 토큰만 넘김, access는 FE가 /reissue)
    record LoggedIn(String refreshToken) implements OAuth2Outcome {
    }

    // email이 다른 provide로 이미 존재 -> 충돌 거부
    record Conflict(Provider provider) implements OAuth2Outcome {
    }

    // 신규 -> 가입 유도(provider, email 담은 서명 티켓)
    record SignupRequired(String ticket) implements OAuth2Outcome {
    }
}
