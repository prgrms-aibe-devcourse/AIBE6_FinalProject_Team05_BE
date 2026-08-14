package com.pokade.domain.watchlist.repository;

import com.pokade.domain.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserId(Long userId);

    boolean existsByUserIdAndCardId(Long userId, Long cardId);

    long countByUserId(Long userId);

    Optional<Watchlist> findByIdAndUserId(Long id, Long userId);

    List<Watchlist> findByIsNotifiedFalse();
}
