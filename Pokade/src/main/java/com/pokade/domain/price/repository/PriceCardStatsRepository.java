package com.pokade.domain.price.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.pokade.domain.card.entity.CardPrice;

// domain.card의 CardPriceRepository를 직접 수정하지 않고 필요한 조회만 담은 별도 리포지토리
// (PriceTradeStatsRepository와 같은 패턴). period별로 6개의 change_*_pct 컬럼 중 하나를 골라야 하는데,
// 메서드를 6개 만드는 대신 네이티브 쿼리의 CASE 식으로 period 값에 따라 컬럼을 동적으로 선택한다.
public interface PriceCardStatsRepository extends Repository<CardPrice, Long> {

    @Query(value = """
            SELECT
              CASE :period
                WHEN '1d' THEN change_1d_pct
                WHEN '7d' THEN change_7d_pct
                WHEN '14d' THEN change_14d_pct
                WHEN '30d' THEN change_30d_pct
                WHEN '90d' THEN change_90d_pct
                WHEN '180d' THEN change_180d_pct
              END AS changePct,
              change_7d_amount AS change7dAmount
            FROM card_prices
            WHERE variant_id = :variantId AND price_type = 'graded' AND grade = :grade AND company = :company
            """, nativeQuery = true)
    Optional<CardPriceChangeView> findChangeByVariantGradeCompanyAndPeriod(
            @Param("variantId") Long variantId,
            @Param("grade") String grade,
            @Param("company") String company,
            @Param("period") String period);

    interface CardPriceChangeView {
        BigDecimal getChangePct();

        BigDecimal getChange7dAmount();
    }
}
