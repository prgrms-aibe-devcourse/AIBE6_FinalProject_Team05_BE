package com.pokade.domain.card.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pokade.domain.card.entity.PokedexKoName;

public interface PokedexKoNameRepository extends JpaRepository<PokedexKoName, Integer> {

    List<PokedexKoName> findByNameKoContaining(String nameKo);

    List<PokedexKoName> findByNameKoChosungContaining(String chosung);

    /**
     * 정확 검색(부분일치)이 0건일 때만 시도하는 오타 허용 폴백 검색(#187) - pg_trgm의 similarity()로
     * name_ko와의 유사도가 threshold 이상인 행을 유사도 내림차순으로 조회한다. V4 마이그레이션에서
     * 추가한 pg_trgm 확장 + GIN 인덱스(idx_pokedex_ko_names_name_ko_trgm)를 전제로 한다.
     */
    @Query(value = """
            SELECT * FROM pokedex_ko_names WHERE similarity(name_ko, :keyword) >= :threshold
            ORDER BY similarity(name_ko, :keyword) DESC
            """,
            nativeQuery = true)
    List<PokedexKoName> findByNameKoSimilarTo(@Param("keyword") String keyword, @Param("threshold") double threshold);
}
