package com.pokade.domain.card.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreanTextUtilTest {

    @Test
    @DisplayName("t1 완성형 한글이 포함되면 true를 반환한다")
    void t1() {
        assertThat(KoreanTextUtil.isKorean("피카츄")).isTrue();
        assertThat(KoreanTextUtil.isKorean("abc피카츄123")).isTrue();
    }

    @Test
    @DisplayName("t2 완성형 한글이 전혀 없으면 false를 반환한다")
    void t2() {
        assertThat(KoreanTextUtil.isKorean("Pikachu")).isFalse();
        assertThat(KoreanTextUtil.isKorean("123")).isFalse();
        assertThat(KoreanTextUtil.isKorean("ㅍㅋㅊ")).isFalse();
    }

    @Test
    @DisplayName("t3 null이면 false를 반환한다")
    void t3() {
        assertThat(KoreanTextUtil.isKorean(null)).isFalse();
    }

    @Test
    @DisplayName("t4 완성형 한글 단어를 초성으로 정확히 변환한다")
    void t4() {
        assertThat(KoreanTextUtil.extractChosung("피카츄")).isEqualTo("ㅍㅋㅊ");
    }

    @Test
    @DisplayName("t5 한글이 아닌 문자는 그대로 유지한다")
    void t5() {
        assertThat(KoreanTextUtil.extractChosung("Pikachu123")).isEqualTo("Pikachu123");
    }

    @Test
    @DisplayName("t6 한글과 영문/숫자가 섞인 경우 한글만 초성으로 변환한다")
    void t6() {
        assertThat(KoreanTextUtil.extractChosung("피카츄ex")).isEqualTo("ㅍㅋㅊex");
    }

    @Test
    @DisplayName("t7 null이면 null을 반환한다")
    void t7() {
        assertThat(KoreanTextUtil.extractChosung(null)).isNull();
    }

    @Test
    @DisplayName("t8 빈 문자열이면 빈 문자열을 반환한다")
    void t8() {
        assertThat(KoreanTextUtil.extractChosung("")).isEmpty();
    }

    @Test
    @DisplayName("t9 전체가 자음(초성)으로만 이뤄지면 true를 반환한다")
    void t9() {
        assertThat(KoreanTextUtil.isChosungOnly("ㅍㅋㅊ")).isTrue();
    }

    @Test
    @DisplayName("t10 완성형 한글이 섞여있으면 false를 반환한다")
    void t10() {
        assertThat(KoreanTextUtil.isChosungOnly("ㅍ카츄")).isFalse();
    }

    @Test
    @DisplayName("t11 영문/숫자가 섞여있으면 false를 반환한다")
    void t11() {
        assertThat(KoreanTextUtil.isChosungOnly("ㅍㅋㅊ123")).isFalse();
    }

    @Test
    @DisplayName("t12 null이면 false를 반환한다")
    void t12() {
        assertThat(KoreanTextUtil.isChosungOnly(null)).isFalse();
    }

    @Test
    @DisplayName("t13 빈 문자열이면 false를 반환한다")
    void t13() {
        assertThat(KoreanTextUtil.isChosungOnly("")).isFalse();
    }
}
