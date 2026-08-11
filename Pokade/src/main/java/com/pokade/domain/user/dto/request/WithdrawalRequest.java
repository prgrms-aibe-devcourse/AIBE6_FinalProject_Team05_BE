package com.pokade.domain.user.dto.request;

public record WithdrawalRequest(
        String password, // LOCAL 계정용 (소셜은 null)
        String code       // 소셜 계정용 이메일 인증코드 (LOCAL은 null)
) {
}