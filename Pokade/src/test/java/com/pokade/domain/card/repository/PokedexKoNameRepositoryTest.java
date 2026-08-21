package com.pokade.domain.card.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.pokade.domain.card.entity.PokedexKoName;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class PokedexKoNameRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PokedexKoNameRepository pokedexKoNameRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        persist(5, "Charmeleon", "리자드", "ㄹㅈㄷ");
        persist(6, "Charizard", "리자몽", "ㄹㅈㅁ");
        persist(25, "Pikachu", "피카츄", "ㅍㅋㅊ");
        persist(7, "Squirtle", "꼬부기", "ㄲㅂㄱ");
    }

    private void persist(int pokedexNumber, String nameEn, String nameKo, String chosung) {
        entityManager.persist(PokedexKoName.builder()
                .pokedexNumber(pokedexNumber)
                .nameEn(nameEn)
                .nameKo(nameKo)
                .nameKoChosung(chosung)
                .build());
    }

    @Test
    @DisplayName("t1 오타(리자옹)와 유사도가 threshold 이상인 이름들을 유사도 내림차순으로 조회한다(#187, pg_trgm)")
    void t1() {
        List<PokedexKoName> result = pokedexKoNameRepository.findByNameKoSimilarTo("리자옹", 0.14);

        assertThat(result).extracting(PokedexKoName::getNameKo)
                .containsExactlyInAnyOrder("리자드", "리자몽");
    }

    @Test
    @DisplayName("t2 오타(핏카츄)와 유사도가 threshold 이상인 이름을 조회한다(#187, pg_trgm)")
    void t2() {
        List<PokedexKoName> result = pokedexKoNameRepository.findByNameKoSimilarTo("핏카츄", 0.14);

        assertThat(result).extracting(PokedexKoName::getNameKo).containsExactly("피카츄");
    }

    @Test
    @DisplayName("t3 오타(꼬부리)와 유사도가 threshold 이상인 이름을 조회한다(#187, pg_trgm)")
    void t3() {
        List<PokedexKoName> result = pokedexKoNameRepository.findByNameKoSimilarTo("꼬부리", 0.14);

        assertThat(result).extracting(PokedexKoName::getNameKo).containsExactly("꼬부기");
    }

    @Test
    @DisplayName("t4 threshold 미달이면 조회되지 않는다(#187, pg_trgm)")
    void t4() {
        List<PokedexKoName> result = pokedexKoNameRepository.findByNameKoSimilarTo("전혀다른이름입니다", 0.14);

        assertThat(result).isEmpty();
    }
}
