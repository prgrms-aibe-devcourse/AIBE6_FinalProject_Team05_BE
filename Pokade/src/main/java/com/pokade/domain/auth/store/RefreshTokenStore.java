package com.pokade.domain.auth.store;

public interface RefreshTokenStore {
    void save(Long userId, String sid, String refreshToken);
    boolean exists(Long userId, String sid);
    boolean compareAndRotate(Long userId, String sid, String presentedToken, String newRefreshToken);
    boolean matchesGrace(Long userId, String sid, String refreshToken);
    void delete(Long userId, String sid);
    void deleteAll(Long userId);

    void save(Long userId, String refreshToken);
    boolean exists(Long userId);
    boolean compareAndRotate(Long userId, String presentedToken, String newRefreshToken);
    boolean matchesGrace(Long userId, String refreshToken);
    void delete(Long userId);
}
