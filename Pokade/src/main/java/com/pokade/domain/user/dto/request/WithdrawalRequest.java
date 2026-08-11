package com.pokade.domain.user.dto.request;

public record WithdrawalRequest(
        String password, // LOCAL 계정용 (소셜은 null)
        String reauthToken // 소셜 계정용 OAuth 재인증 proof 티켓 (LOCAL은 null)
) {
}
