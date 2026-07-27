package com.pokade.domain.auth.service;

public interface VerificationCodeStore {
    boolean isRecentlySent(String email);
    void save(String email, String code);
}
