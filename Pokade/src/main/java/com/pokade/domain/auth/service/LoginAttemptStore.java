package com.pokade.domain.auth.service;

public interface LoginAttemptStore {
    void recordFailure(String username);
    boolean isBlocked(String username);
    void reset(String username);
}
