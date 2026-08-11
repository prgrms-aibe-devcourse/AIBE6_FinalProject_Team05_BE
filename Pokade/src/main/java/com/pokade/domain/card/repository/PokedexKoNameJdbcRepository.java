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

    /**
     * ⚠️ INSERT 컬럼 목록(pokedex_number, name_en, name_ko, name_ko_chosung)이 PokedexKoName 엔티티와
     * 별개로 SQL 문자열에 하드코딩되어 있다. 엔티티에 컬럼을 추가/변경하면 이 클래스도 반드시
     * 함께 수정해야 한다 - 안 그러면 컴파일 에러 없이 새 컬럼이 조용히 누락된다.
     */
    public void batchUpsert(List<PokedexKoName> pokedexKoNames) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO pokedex_ko_names (pokedex_number, name_en, name_ko, name_ko_chosung) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (pokedex_number) DO NOTHING",
                pokedexKoNames,
                pokedexKoNames.size(),
                (ps, item) -> {
                    ps.setInt(1, item.getPokedexNumber());
                    ps.setString(2, item.getNameEn());
                    ps.setString(3, item.getNameKo());
                    ps.setString(4, item.getNameKoChosung());
                });
    }
}
