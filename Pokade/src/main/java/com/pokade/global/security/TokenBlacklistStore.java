package com.pokade.global.security;

public interface TokenBlacklistStore {
    void blacklist(Long userId);
    boolean contains(Long userId);
}
