package com.pokade.domain.price.repository;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// FR-PRICE-04(시세 등락률/거래량) 전용 조회. trades 테이블은 domain.trade의 Trade/TradeRepository가 이미 소유하고
// 있으므로, domain.price는 그 리포지토리를 직접 수정하지 않고 읽기 전용 쿼리만 담은 별도 리포지토리로 필요한 조회를 추가한다.
public interface PriceTradeStatsRepository extends Repository<Trade, Long> {

    // 등락률 비교의 "최근 블록" 평균가(최근 N일 이내, 상한 없음)
    @Query("SELECT AVG(t.price) FROM Trade t JOIN t.listing l "
            + "WHERE l.cardId = :cardId AND l.grade = :grade AND t.status = :status AND t.confirmedAt >= :from")
    Double findAveragePriceByGradeSince(@Param("cardId") Long cardId,
                                         @Param("grade") ListingGrade grade,
                                         @Param("status") TradeStatus status,
                                         @Param("from") LocalDateTime from);

    // 등락률 비교의 "이전 블록" 평균가(직전 N일, [from, to) 구간)
    @Query("SELECT AVG(t.price) FROM Trade t JOIN t.listing l "
            + "WHERE l.cardId = :cardId AND l.grade = :grade AND t.status = :status "
            + "AND t.confirmedAt >= :from AND t.confirmedAt < :to")
    Double findAveragePriceByGradeBetween(@Param("cardId") Long cardId,
                                           @Param("grade") ListingGrade grade,
                                           @Param("status") TradeStatus status,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Trade t JOIN t.listing l "
            + "WHERE l.cardId = :cardId AND l.grade = :grade AND t.status = :status AND t.confirmedAt >= :from")
    long countCompletedTradesByGradeSince(@Param("cardId") Long cardId,
                                           @Param("grade") ListingGrade grade,
                                           @Param("status") TradeStatus status,
                                           @Param("from") LocalDateTime from);
}
