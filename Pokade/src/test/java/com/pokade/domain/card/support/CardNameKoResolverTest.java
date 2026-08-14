package com.pokade.domain.card.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardNameKoResolverTest {

    @Mock
    private PokedexKoNameCache pokedexKoNameCache;

    @InjectMocks
    private CardNameKoResolver cardNameKoResolver;

    @Test
    @DisplayName("t1 pokedexNumbers가 null이면 null을 반환한다")
    void t1() {
        assertThat(cardNameKoResolver.resolve("Charizard", null)).isNull();
    }

    @Test
    @DisplayName("t2 pokedexNumbers가 빈 리스트면 null을 반환한다")
    void t2() {
        assertThat(cardNameKoResolver.resolve("Charizard", List.of())).isNull();
    }

    @Test
    @DisplayName("t3 매핑 자체가 없으면(캐시에서 nameEn/nameKo가 null) null을 반환한다")
    void t3() {
        given(pokedexKoNameCache.getNameEn(6)).willReturn(null);

        assertThat(cardNameKoResolver.resolve("Charizard", List.of(6))).isNull();
    }

    @Test
    @DisplayName("t4 순수 영문 카드 이름인데 nameEn이 포함되어 있지 않으면(종 불일치) null을 반환한다")
    void t4() {
        given(pokedexKoNameCache.getNameEn(6)).willReturn("Charizard");
        given(pokedexKoNameCache.getNameKo(6)).willReturn("리자몽");

        assertThat(cardNameKoResolver.resolve("Blastoise", List.of(6))).isNull();
    }

    @Test
    @DisplayName("t5 정상 매칭 시 카드 이름의 종명 부분만 정확히 치환되고 나머지는 그대로 유지된다")
    void t5() {
        given(pokedexKoNameCache.getNameEn(6)).willReturn("Charizard");
        given(pokedexKoNameCache.getNameKo(6)).willReturn("리자몽");

        assertThat(cardNameKoResolver.resolve("Charizard ex", List.of(6))).isEqualTo("리자몽 ex");
    }

    @Test
    @DisplayName("t6 pokedexNumbers에 여러 번호가 있으면 첫 번째 번호만 사용한다")
    void t6() {
        given(pokedexKoNameCache.getNameEn(6)).willReturn("Charizard");
        given(pokedexKoNameCache.getNameKo(6)).willReturn("리자몽");

        assertThat(cardNameKoResolver.resolve("Charizard ex", List.of(6, 9))).isEqualTo("리자몽 ex");
    }

    @Test
    @DisplayName("t7 대소문자가 다르면(카드 이름은 소문자, nameEn은 대문자 시작) 매칭되지 않고 null을 반환한다")
    void t7() {
        given(pokedexKoNameCache.getNameEn(6)).willReturn("Charizard");
        given(pokedexKoNameCache.getNameKo(6)).willReturn("리자몽");

        assertThat(cardNameKoResolver.resolve("charizard ex", List.of(6))).isNull();
    }

    @Test
    @DisplayName("t8 비영문+영문 접미사 조합이면 접미사만 분리하고 앞부분을 nameKo로 치환한다")
    void t8() {
        given(pokedexKoNameCache.getNameEn(940)).willReturn("Toedscruel");
        given(pokedexKoNameCache.getNameKo(940)).willReturn("파이코");

        assertThat(cardNameKoResolver.resolve("クヌギダマex", List.of(940))).isEqualTo("파이코ex");
    }

    @Test
    @DisplayName("t9 순수 비영문 카드 이름(접미사 없음)이면 nameKo만 그대로 반환한다")
    void t9() {
        given(pokedexKoNameCache.getNameEn(940)).willReturn("Toedscruel");
        given(pokedexKoNameCache.getNameKo(940)).willReturn("파이코");

        assertThat(cardNameKoResolver.resolve("クヌギダマ", List.of(940))).isEqualTo("파이코");
    }

    @Test
    @DisplayName("t10 pokedexNumbers의 첫 번째 원소가 null이면 NPE 없이 null을 반환한다(원본 이름 폴백)")
    void t10() {
        assertThat(cardNameKoResolver.resolve("Charizard", Arrays.asList(null, 6))).isNull();

        then(pokedexKoNameCache).should(never()).getNameEn(org.mockito.ArgumentMatchers.any());
        then(pokedexKoNameCache).should(never()).getNameKo(org.mockito.ArgumentMatchers.any());
    }
}
