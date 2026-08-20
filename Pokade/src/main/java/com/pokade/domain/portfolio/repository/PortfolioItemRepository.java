package com.pokade.domain.portfolio.repository;

import com.pokade.domain.portfolio.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findByUserIdOrderByIdDesc(Long userId);

    Optional<PortfolioItem> findByIdAndUserId(Long id, Long userId);

    boolean existsByTradeId(Long tradeId);

    boolean existsByGradeResultId(Long gradeResultId);
}
