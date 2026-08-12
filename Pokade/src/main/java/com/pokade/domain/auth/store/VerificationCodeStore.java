package com.pokade.domain.auth.store;

public interface VerificationCodeStore {
    boolean save(String email, String code);
    VerificationResult verifyAndConsume(String email, String code);
}