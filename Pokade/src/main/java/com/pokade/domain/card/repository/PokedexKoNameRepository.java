package com.pokade.domain.card.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokade.domain.card.entity.PokedexKoName;

public interface PokedexKoNameRepository extends JpaRepository<PokedexKoName, Integer> {

    Optional<PokedexKoName> findByNameKo(String nameKo);
}
