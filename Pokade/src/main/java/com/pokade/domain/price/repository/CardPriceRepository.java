package com.pokade.domain.price.repository;

import com.pokade.domain.price.entity.CardPrice;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CardPriceRepository extends JpaRepository<CardPrice, Long> {

    @Query("SELECT cp FROM CardPrice cp JOIN FETCH cp.variant v JOIN FETCH v.card c "
            + "WHERE cp.change7dPct IS NOT NULL ORDER BY cp.change7dPct DESC")
    List<CardPrice> findTopRising(Pageable pageable);

    @Query("SELECT cp FROM CardPrice cp JOIN FETCH cp.variant v JOIN FETCH v.card c "
            + "WHERE cp.change7dPct IS NOT NULL ORDER BY cp.change7dPct ASC")
    List<CardPrice> findTopFalling(Pageable pageable);
}
