package com.pokade.domain.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokade.domain.card.entity.Expansion;

public interface ExpansionRepository extends JpaRepository<Expansion, String> {
}
