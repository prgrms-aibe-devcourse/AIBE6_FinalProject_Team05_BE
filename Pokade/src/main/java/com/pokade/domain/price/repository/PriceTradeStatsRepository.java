package com.pokade.domain.price.repository;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;

import java.time.LocalDate;
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

    // 카드별 최근 체결가 1건: confirmedAt 동률 시 MAX()만으로는 2건이 반환돼 Collectors.toMap()이
    // 깨지므로 DISTINCT ON으로 확정한다. 네이티브 쿼리라 enum은 문자열로 변환해서 넘긴다.
    default List<CardPriceView> findRecentCompletedTradePricesByCardIds(List<Long> cardIds, ListingGrade grade,
                                                                          TradeStatus status) {
        return findRecentCompletedTradePricesByCardIdsNative(cardIds, grade == null ? null : grade.name(),
                status.name());
    }

    @Query(value = """
            SELECT DISTINCT ON (l.card_id) l.card_id AS cardId, t.price AS price
            FROM trades t JOIN listings l ON l.id = t.listing_id
            WHERE l.card_id IN (:cardIds) AND (:grade IS NULL OR l.grade = :grade) AND t.status = :status
            ORDER BY l.card_id, t.confirmed_at DESC, t.id DESC
            """, nativeQuery = true)
    List<CardPriceView> findRecentCompletedTradePricesByCardIdsNative(@Param("cardIds") List<Long> cardIds,
                                                                        @Param("grade") String grade,
                                                                        @Param("status") String status);

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

    /**
     * 워치리스트 "목표가 도달" 판정용(#275): 여러 워치리스트를 배치로 조회할 때, 카드마다 서로 다른
     * 등록(createdAt) 시점을 각각 적용해서 최저/최고가를 구한다. cardIds[i]와 sinceList[i]가 짝을
     * 이룬다(같은 인덱스끼리 매칭) - 길이가 다르면 안 된다.
     *
     * 네이티브 쿼리에서 enum 파라미터를 그대로 바인딩하면 Hibernate가 ordinal(숫자)로 보내
     * "character varying = smallint" 타입 오류가 난다(#275 스파이크로 실측 확인) - 그래서 이 default
     * 메서드가 enum을 문자열로 변환해 실제 네이티브 쿼리 메서드에 넘긴다. 호출부는 기존 메서드들과
     * 동일하게 enum을 그대로 넘기면 된다.
     */
    default List<CardPriceRangeView> findPriceRangesByCardIdsSincePerCard(List<Long> cardIds,
                                                                           List<LocalDateTime> sinceList,
                                                                           ListingGrade grade,
                                                                           TradeStatus status) {
        return findPriceRangesByCardIdsSincePerCardNative(
                cardIds.toArray(Long[]::new),
                sinceList.toArray(LocalDateTime[]::new),
                grade == null ? null : grade.name(),
                status.name());
    }

    @Query(value = """
            SELECT pairs.card_id AS cardId, MIN(t.price) AS minPrice, MAX(t.price) AS maxPrice
            FROM unnest(:cardIds, :sinceList) AS pairs(card_id, since)
            JOIN listings l ON l.card_id = pairs.card_id
            JOIN trades t ON t.listing_id = l.id
            WHERE t.status = :status AND t.confirmed_at >= pairs.since
              AND (:grade IS NULL OR l.grade = :grade)
            GROUP BY pairs.card_id
            """, nativeQuery = true)
    List<CardPriceRangeView> findPriceRangesByCardIdsSincePerCardNative(@Param("cardIds") Long[] cardIds,
                                                                         @Param("sinceList") LocalDateTime[] sinceList,
                                                                         @Param("grade") String grade,
                                                                         @Param("status") String status);

    /**
     * 시세 랭킹 페이지의 "거래 현황" 개요용: 플랫폼 전체(카드/등급 구분 없음) 체결가를 일 단위로 묶어
     * 거래 건수와 거래가 중간값(median)을 계산한다. 평균(AVG)이 아니라 PERCENTILE_CONT(0.5)로 중간값을
     * 구하는 이유는 소수의 초고가/초저가 체결이 평균을 크게 왜곡할 수 있어서다(FR-PRICE-06 랭킹의
     * 평균 기반 등락률과는 별개 지표). JPQL은 PERCENTILE_CONT/날짜 절삭을 지원하지 않아 네이티브 쿼리로
     * 작성했고, enum을 네이티브 쿼리에 그대로 바인딩하면 ordinal로 전송되는 문제(findPriceRangesByCardIdsSincePerCard
     * 주석 참고)와 동일하게 상태값도 문자열로 변환해서 넘긴다.
     */
    default List<DailyMarketStatView> findDailyMarketStats(TradeStatus status, LocalDateTime from) {
        return findDailyMarketStatsNative(status.name(), from);
    }

    @Query(value = """
            SELECT CAST(t.confirmed_at AS date) AS tradeDate,
                   COUNT(*) AS volume,
                   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY t.price) AS medianPrice
            FROM trades t
            WHERE t.status = :status AND t.confirmed_at >= :from
            GROUP BY CAST(t.confirmed_at AS date)
            ORDER BY tradeDate
            """, nativeQuery = true)
    List<DailyMarketStatView> findDailyMarketStatsNative(@Param("status") String status,
                                                          @Param("from") LocalDateTime from);

    interface DailyMarketStatView {
        LocalDate getTradeDate();
        Long getVolume();
        Double getMedianPrice();
    }
}
