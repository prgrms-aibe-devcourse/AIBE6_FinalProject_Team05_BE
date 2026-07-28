package com.pokade.domain.auth.service;

import java.util.Optional;

public interface VerificationCodeStore {
    boolean isRecentlySent(String email);
    void save(String email, String code);
    Optional<String> find (String email);
    void delete(String email);
    long getAttemptCount(String email); // 현재 실패 횟수 조회
    void incrementAttempt(String email); // 실패시 +1
}
