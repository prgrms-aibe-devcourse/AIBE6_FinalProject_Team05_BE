package com.pokade.domain.auth.service;

public interface RefreshTokenStore {
    void save(Long userId, String refreshToken);
    String find(Long userId);
    void delete(Long userId);
    void saveGrace(Long userId, String refreshToken);
    String findGrace(Long userId);
}
