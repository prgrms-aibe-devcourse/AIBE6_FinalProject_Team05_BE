package com.pokade.domain.card.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokade.domain.card.entity.PokedexKoName;

public interface PokedexKoNameRepository extends JpaRepository<PokedexKoName, Integer> {

    List<PokedexKoName> findByNameKoContaining(String nameKo);

    List<PokedexKoName> findByNameKoChosungContaining(String chosung);
}
