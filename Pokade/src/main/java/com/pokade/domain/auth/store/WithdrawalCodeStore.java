package com.pokade.domain.auth.store;

import java.util.Optional;

public interface WithdrawalCodeStore {
    boolean save(String email, String code);
    Optional<String> find(String email);
    void delete(String email);
    long getAttemptCount(String email);
    void incrementAttempt(String email);
}
