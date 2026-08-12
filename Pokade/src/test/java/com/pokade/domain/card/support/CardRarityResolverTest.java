package com.pokade.domain.card.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CardRarityResolverTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("t1 매핑된 rarity_code는 표준 명칭으로 치환된다")
    @CsvSource({
            "●, Common",
            "★H, Rare Holo",
            "◇◇, Double Rare",
            "☆1, Illustration Rare",
            "EX, Rare Holo EX",
            "GX, Rare Holo GX"
    })
    void t1(String rarityCode, String expected) {
        assertThat(CardRarityResolver.resolve(rarityCode, "원본값")).isEqualTo(expected);
    }

    @Test
    @DisplayName("t2 매핑에 없는 rarity_code는 원본 rarity 값을 그대로 반환한다")
    void t2() {
        assertThat(CardRarityResolver.resolve("★★★", "Hyper Rare")).isEqualTo("Hyper Rare");
    }

    @Test
    @DisplayName("t3 rarity_code가 null이면 원본 rarity 값을 그대로 반환한다")
    void t3() {
        assertThat(CardRarityResolver.resolve(null, "Rare Holo")).isEqualTo("Rare Holo");
    }

    @Test
    @DisplayName("t4 rarity_code와 rarity가 모두 null이어도 예외 없이 null을 반환한다")
    void t4() {
        assertThat(CardRarityResolver.resolve(null, null)).isNull();
    }
}
