package com.pokade.domain.card.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

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
            "GX, Rare Holo GX",
            "C, Common",
            "R, Rare",
            "U, Uncommon"
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

    @Test
    @DisplayName("t5 표준 레어도명을 역매핑하면 알려진 원본(다국어) 텍스트와 표준명 자신을 함께 반환한다")
    void t5() {
        assertThat(CardRarityResolver.resolveOriginalValues(List.of("Common"))).containsExactlyInAnyOrder("Common", "通常");
    }

    @Test
    @DisplayName("t5-1 표준 레어도명 Rare를 역매핑하면 알려진 원본(다국어) 텍스트와 표준명 자신을 함께 반환한다")
    void t5_1() {
        assertThat(CardRarityResolver.resolveOriginalValues(List.of("Rare"))).containsExactlyInAnyOrder("Rare", "希少");
    }

    @Test
    @DisplayName("t5-2 표준 레어도명 Uncommon을 역매핑하면 알려진 원본(다국어) 텍스트와 표준명 자신을 함께 반환한다")
    void t5_2() {
        assertThat(CardRarityResolver.resolveOriginalValues(List.of("Uncommon"))).containsExactlyInAnyOrder("Uncommon", "非");
    }

    @Test
    @DisplayName("t6 원본 텍스트가 알려지지 않은 표준 레어도명은 표준명 자신만 포함된다")
    void t6() {
        assertThat(CardRarityResolver.resolveOriginalValues(List.of("Rare Holo"))).containsExactly("Rare Holo");
    }

    @Test
    @DisplayName("t7 매핑에 없는 임의 값은 원본 값 그대로만 포함된다")
    void t7() {
        assertThat(CardRarityResolver.resolveOriginalValues(List.of("Secret Rare"))).containsExactly("Secret Rare");
    }

    @Test
    @DisplayName("t8 역매핑 대상이 null이면 null을 반환한다")
    void t8() {
        assertThat(CardRarityResolver.resolveOriginalValues(null)).isNull();
    }

    @Test
    @DisplayName("t9 리스트에 null 원소가 섞여 있어도 NPE 없이 나머지 원소만 정상 처리된다")
    void t9() {
        assertThat(CardRarityResolver.resolveOriginalValues(Arrays.asList("Common", null)))
                .containsExactlyInAnyOrder("Common", "通常");
    }

    @Test
    @DisplayName("t10 리스트가 전부 null이면 빈 리스트를 반환한다")
    void t10() {
        assertThat(CardRarityResolver.resolveOriginalValues(Arrays.asList(null, null))).isEmpty();
    }
}
