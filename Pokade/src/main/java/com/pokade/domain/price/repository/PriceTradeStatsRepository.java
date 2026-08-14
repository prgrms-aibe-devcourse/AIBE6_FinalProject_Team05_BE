package com.pokade.domain.price.repository;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// domain.trade의 TradeRepository를 직접 수정하지 않고 필요한 조회만 담은 별도 리포지토리.
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

    // 카드별 가장 최근 체결가 1건(grade 지정 시 해당 등급만)
    @Query("SELECT l.cardId AS cardId, t.price AS price FROM Trade t JOIN t.listing l "
            + "WHERE l.cardId IN (:cardIds) AND (:grade IS NULL OR l.grade = :grade) AND t.status = :status "
            + "AND t.confirmedAt = ("
            + "    SELECT MAX(t2.confirmedAt) FROM Trade t2 JOIN t2.listing l2 "
            + "    WHERE l2.cardId = l.cardId AND (:grade IS NULL OR l2.grade = :grade) AND t2.status = :status"
            + ")")
    List<CardPriceView> findRecentCompletedTradePricesByCardIds(@Param("cardIds") List<Long> cardIds,
                                                                  @Param("grade") ListingGrade grade,
                                                                  @Param("status") TradeStatus status);

    interface CardPriceView {
        Long getCardId();
        Integer getPrice();
    }

    // 랭킹용: 카드별 "최근 블록" 평균가를 한 번에 조회 (거래가 있는 카드만 결과에 포함)
    @Query("SELECT l.cardId AS cardId, AVG(t.price) AS avgPrice FROM Trade t JOIN t.listing l "
            + "WHERE l.grade = :grade AND t.status = :status AND t.confirmedAt >= :from "
            + "GROUP BY l.cardId")
    List<CardAvgPriceView> findAveragePricesByGradeSince(@Param("grade") ListingGrade grade,
                                                          @Param("status") TradeStatus status,
                                                          @Param("from") LocalDateTime from);

    // 랭킹용: 카드별 "이전 블록" 평균가를 한 번에 조회 ([from, to) 구간)
    @Query("SELECT l.cardId AS cardId, AVG(t.price) AS avgPrice FROM Trade t JOIN t.listing l "
            + "WHERE l.grade = :grade AND t.status = :status AND t.confirmedAt >= :from AND t.confirmedAt < :to "
            + "GROUP BY l.cardId")
    List<CardAvgPriceView> findAveragePricesByGradeBetween(@Param("grade") ListingGrade grade,
                                                            @Param("status") TradeStatus status,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("to") LocalDateTime to);

    interface CardAvgPriceView {
        Long getCardId();
        Double getAvgPrice();
    }

    // 워치리스트 "목표가 도달" 판정용: 카드별 체결가 전체 기간 최저/최고가를 한 번에 조회
    @Query("SELECT l.cardId AS cardId, MIN(t.price) AS minPrice, MAX(t.price) AS maxPrice FROM Trade t JOIN t.listing l "
            + "WHERE l.cardId IN (:cardIds) AND (:grade IS NULL OR l.grade = :grade) AND t.status = :status "
            + "GROUP BY l.cardId")
    List<CardPriceRangeView> findPriceRangesByCardIds(@Param("cardIds") List<Long> cardIds,
                                                       @Param("grade") ListingGrade grade,
                                                       @Param("status") TradeStatus status);

    interface CardPriceRangeView {
        Long getCardId();
        Integer getMinPrice();
        Integer getMaxPrice();
    }

    // 워치리스트 "목표가 도달" 판정용: 워치리스트 등록(createdAt) 이후 체결된 것만 카드별 최저/최고가 조회
    @Query("SELECT l.cardId AS cardId, MIN(t.price) AS minPrice, MAX(t.price) AS maxPrice FROM Trade t JOIN t.listing l "
            + "WHERE l.cardId IN (:cardIds) AND (:grade IS NULL OR l.grade = :grade) AND t.status = :status "
            + "AND t.confirmedAt >= :from "
            + "GROUP BY l.cardId")
    List<CardPriceRangeView> findPriceRangesByCardIdsSince(@Param("cardIds") List<Long> cardIds,
                                                            @Param("grade") ListingGrade grade,
                                                            @Param("status") TradeStatus status,
                                                            @Param("from") LocalDateTime from);
}
