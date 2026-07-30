package com.pokade.domain.auth.dto;

public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
