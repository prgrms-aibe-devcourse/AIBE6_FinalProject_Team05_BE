package com.pokade.global.port;

public interface UserAccessChecker {
    void assertWritable(Long userId);
}
