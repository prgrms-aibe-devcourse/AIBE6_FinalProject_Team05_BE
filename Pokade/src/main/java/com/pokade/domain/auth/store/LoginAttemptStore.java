package com.pokade.domain.auth.store;

public interface LoginAttemptStore {
    void recordFailure(String username);
    boolean isBlocked(String username);
    void reset(String username);
}
