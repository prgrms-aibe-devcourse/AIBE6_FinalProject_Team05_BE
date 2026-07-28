package com.pokade.domain.trade;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    @Query("SELECT t FROM Trade t JOIN FETCH t.listing l "
            + "WHERE l.cardId = :cardId AND t.status = :status "
            + "ORDER BY t.confirmedAt DESC")
    List<Trade> findRecentCompletedTrades(@Param("cardId") Long cardId,
                                           @Param("status") TradeStatus status,
                                           Pageable pageable);
}
