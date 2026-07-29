package com.pokade.domain.auth.service;

public interface RefreshTokenStore {
    void save(Long userId, String refreshToken);
}
