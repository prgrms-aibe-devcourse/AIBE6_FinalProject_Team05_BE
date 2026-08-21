package com.pokade.domain.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchlistVariantResolverTest {

    @Test
    @DisplayName("variantId가 있으면 그 값을 그대로 반환한다")
    void resolveOrPrimary_returnsVariantIdWhenPresent() {
        assertThat(WatchlistVariantResolver.resolveOrPrimary(100L, 999L)).isEqualTo(100L);
    }

    @Test
    @DisplayName("variantId가 null이면 대표 variant ID로 치환한다")
    void resolveOrPrimary_fallsBackToPrimaryWhenVariantIdNull() {
        assertThat(WatchlistVariantResolver.resolveOrPrimary(null, 999L)).isEqualTo(999L);
    }

    @Test
    @DisplayName("variantId와 대표 variant ID가 둘 다 null이면 null을 반환한다(NPE 없음)")
    void resolveOrPrimary_returnsNullWhenBothNull() {
        assertThat(WatchlistVariantResolver.resolveOrPrimary(null, null)).isNull();
    }
}
