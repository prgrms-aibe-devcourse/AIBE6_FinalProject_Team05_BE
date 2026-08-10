package com.pokade.domain.user.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class AnonymizationTokenGeneratorTest {
    @Test
    void generate_는_12자리_hex를_반환한다() {
        assertThat(new AnonymizationTokenGenerator().generate()).matches("[0-9a-f]{12}");
    }
}