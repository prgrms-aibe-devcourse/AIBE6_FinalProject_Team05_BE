package com.pokade.domain.card.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CardTypeEnResolverTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("t1 매핑된 일본어 타입명은 영문 타입명으로 치환된다")
    @CsvSource({
            "草, Grass",
            "炎, Fire",
            "水, Water",
            "雷, Lightning",
            "超, Psychic",
            "闘, Fighting",
            "悪, Darkness",
            "鋼, Metal",
            "フェアリー, Fairy",
            "ドラゴン, Dragon",
            "無色, Colorless"
    })
    void t1(String japanese, String expected) {
        assertThat(CardTypeEnResolver.resolve(List.of(japanese))).containsExactly(expected);
    }

    @Test
    @DisplayName("t2 매핑에 없는 값(이미 영문 등)은 원본 그대로 유지한다")
    void t2() {
        assertThat(CardTypeEnResolver.resolve(List.of("Fire", "UnknownType"))).containsExactly("Fire", "UnknownType");
    }

    @Test
    @DisplayName("t3 types가 null이면 null을 반환한다")
    void t3() {
        assertThat(CardTypeEnResolver.resolve(null)).isNull();
    }

    @Test
    @DisplayName("t4 표준 영문 타입명을 역매핑하면 원본(일본어) 텍스트와 표준명 자신을 함께 반환한다")
    void t4() {
        assertThat(CardTypeEnResolver.resolveOriginalValues(List.of("Fire"))).containsExactlyInAnyOrder("Fire", "炎");
    }

    @Test
    @DisplayName("t5 여러 표준 타입명을 역매핑하면 각각의 원본 텍스트가 모두 포함된다")
    void t5() {
        assertThat(CardTypeEnResolver.resolveOriginalValues(List.of("Fire", "Water")))
                .containsExactlyInAnyOrder("Fire", "炎", "Water", "水");
    }

    @Test
    @DisplayName("t6 역매핑에 없는 값(신규/알 수 없는 타입)은 원본 값 그대로만 포함된다")
    void t6() {
        assertThat(CardTypeEnResolver.resolveOriginalValues(List.of("Rock"))).containsExactly("Rock");
    }

    @Test
    @DisplayName("t7 역매핑 대상이 null이면 null을 반환한다")
    void t7() {
        assertThat(CardTypeEnResolver.resolveOriginalValues(null)).isNull();
    }
}
