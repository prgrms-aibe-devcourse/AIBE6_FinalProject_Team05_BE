package com.pokade.domain.trade.repository;

import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;
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

    @Query("SELECT t FROM Trade t JOIN FETCH t.listing l "
            + "WHERE l.cardId = :cardId AND t.status = :status AND t.confirmedAt >= :from "
            + "ORDER BY t.confirmedAt ASC")
    List<Trade> findCompletedTradesSince(@Param("cardId") Long cardId,
                                          @Param("status") TradeStatus status,
                                          @Param("from") LocalDateTime from);
}
