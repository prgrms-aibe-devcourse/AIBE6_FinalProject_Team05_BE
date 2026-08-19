package com.pokade.domain.trade.repository;

import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    // 회원탈퇴 확정 정리용: 탈퇴한 유저가 구매자 또는 판매자(매물 소유자)로 참여 중인 미종결 거래 조회
    @Query("SELECT t FROM Trade t JOIN FETCH t.listing l "
            + "WHERE (t.buyerId = :userId OR l.sellerId = :userId) AND t.status IN :statuses")
    List<Trade> findByParticipantIdAndStatusIn(@Param("userId") Long userId,
                                                @Param("statuses") List<TradeStatus> statuses);

    // 공개 프로필용: 확정된 거래 수 ( 구매자, 판매자 양쪽 합산)
    @Query("SELECT COUNT(t) FROM Trade t JOIN t.listing l "
            + "WHERE (t.buyerId = :userId OR l.sellerId = :userId) AND t.status = :status")
    long countByParticipantIdAndStatus(@Param("userId") Long userId,
                                       @Param("status") TradeStatus status);

    // 관리자 검수/배송 처리 대기 목록: SHIPPED_TO_PLATFORM(검수 대기), INSPECTED(배송 대기) 거래
    @Query("SELECT t FROM Trade t JOIN FETCH t.listing l "
            + "WHERE t.status IN :statuses ORDER BY t.createdAt ASC")
    List<Trade> findByStatusInOrderByCreatedAtAsc(@Param("statuses") List<TradeStatus> statuses);
}
