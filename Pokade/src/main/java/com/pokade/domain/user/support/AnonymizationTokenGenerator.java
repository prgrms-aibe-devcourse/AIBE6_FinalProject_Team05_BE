package com.pokade.domain.user.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AnonymizationTokenGenerator {

    public String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
