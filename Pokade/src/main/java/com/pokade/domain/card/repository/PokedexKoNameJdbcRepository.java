package com.pokade.domain.card.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.pokade.domain.card.entity.PokedexKoName;

import lombok.RequiredArgsConstructor;

/**
 * pokedex_ko_names 배치 upsert 전용. 이 저장 방식은 Spring Data JPA 메서드 이름 규칙으로
 * 표현할 수 없는 네이티브 배치 SQL(ON CONFLICT DO NOTHING)이라 JpaRepository가 아닌
 * 별도 클래스로 분리했다.
 */
@Repository
@RequiredArgsConstructor
public class PokedexKoNameJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public void batchUpsert(List<PokedexKoName> pokedexKoNames) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO pokedex_ko_names (pokedex_number, name_ko, name_ko_chosung) "
                        + "VALUES (?, ?, ?) ON CONFLICT (pokedex_number) DO NOTHING",
                pokedexKoNames,
                pokedexKoNames.size(),
                (ps, item) -> {
                    ps.setInt(1, item.getPokedexNumber());
                    ps.setString(2, item.getNameKo());
                    ps.setString(3, item.getNameKoChosung());
                });
    }
}
