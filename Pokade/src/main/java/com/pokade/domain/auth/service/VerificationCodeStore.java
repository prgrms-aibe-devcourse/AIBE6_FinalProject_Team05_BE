package com.pokade.domain.auth.service;

import java.util.Optional;

public interface VerificationCodeStore {
    boolean save(String email, String code);
    Optional<String> find(String email);
    void delete(String email);
    long getAttemptCount(String email);
    void incrementAttempt(String email);
}
