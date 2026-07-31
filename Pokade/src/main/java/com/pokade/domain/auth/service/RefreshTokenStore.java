package com.pokade.domain.auth.service;

public interface RefreshTokenStore {
    void save(Long userId, String refreshToken);
    boolean exists(Long userId);
    boolean matches(Long userId, String refreshToken);
    void delete(Long userId);
    void saveGrace(Long userId, String refreshToken);
    boolean matchesGrace(Long userId, String refreshToken);
}
