package com.pokade.domain.auth.service;

import com.pokade.domain.auth.support.VerificationCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeGeneratorTest {

    private final VerificationCodeGenerator generator = new VerificationCodeGenerator();

    @RepeatedTest(20)
    @DisplayName("6자리 숫자 코드를 생성한다 (앞자리 0 포함)")
    void generatesSixDigitNumericCode() {
        String code = generator.generate();

        assertThat(code).matches("\\d{6}"); // 정확히 숫자 6자리
    }
}