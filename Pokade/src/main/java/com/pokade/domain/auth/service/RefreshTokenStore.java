package com.pokade.domain.auth.service;

public interface RefreshTokenStore {
    void save(Long userId, String refreshToken);
    boolean exists(Long userId);
    boolean compareAndRotate(Long userId, String presentedToken, String newRefreshToken);
    boolean matchesGrace(Long userId, String refreshToken);
    void delete(Long userId);
}
