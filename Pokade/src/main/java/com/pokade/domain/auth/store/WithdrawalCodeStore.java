package com.pokade.domain.auth.store;

public interface WithdrawalCodeStore {
    boolean save(String email, String code);
    VerificationResult verifyAndConsume(String email, String code);
}