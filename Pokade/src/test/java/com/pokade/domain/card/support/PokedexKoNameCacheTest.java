package com.pokade.domain.card.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.domain.card.repository.PokedexKoNameRepository;

@ExtendWith(MockitoExtension.class)
class PokedexKoNameCacheTest {

    @Mock
    private PokedexKoNameRepository pokedexKoNameRepository;

    @InjectMocks
    private PokedexKoNameCache pokedexKoNameCache;

    @Test
    @DisplayName("t1 reload() 전에는 getNameEn/getNameKo가 null을 반환한다")
    void t1() {
        assertThat(pokedexKoNameCache.getNameEn(6)).isNull();
        assertThat(pokedexKoNameCache.getNameKo(6)).isNull();
    }

    @Test
    @DisplayName("t2 reload() 후에는 저장된 데이터가 정확히 조회된다")
    void t2() {
        PokedexKoName charizard = PokedexKoName.builder()
                .pokedexNumber(6)
                .nameEn("Charizard")
                .nameKo("리자몽")
                .nameKoChosung("ㄹㅈㅁ")
                .build();
        given(pokedexKoNameRepository.findAll()).willReturn(List.of(charizard));

        pokedexKoNameCache.reload();

        assertThat(pokedexKoNameCache.getNameEn(6)).isEqualTo("Charizard");
        assertThat(pokedexKoNameCache.getNameKo(6)).isEqualTo("리자몽");
    }

    @Test
    @DisplayName("t3 존재하지 않는 도감번호로 조회하면 null을 반환한다")
    void t3() {
        PokedexKoName charizard = PokedexKoName.builder()
                .pokedexNumber(6)
                .nameEn("Charizard")
                .nameKo("리자몽")
                .nameKoChosung("ㄹㅈㅁ")
                .build();
        given(pokedexKoNameRepository.findAll()).willReturn(List.of(charizard));

        pokedexKoNameCache.reload();

        assertThat(pokedexKoNameCache.getNameEn(999)).isNull();
        assertThat(pokedexKoNameCache.getNameKo(999)).isNull();
    }
}
