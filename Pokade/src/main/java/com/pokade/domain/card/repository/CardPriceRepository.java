package com.pokade.domain.card.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pokade.domain.card.entity.CardPrice;

public interface CardPriceRepository extends JpaRepository<CardPrice, Long> {

    Optional<CardPrice> findByVariantIdAndPriceTypeAndGradeAndCompany(
            Long variantId, String priceType, String grade, String company);

    // 검색 목록의 "가격 정보 없음" fallback용 - 여러 판본의 비등급(raw) 시세를 한 번에 조회(가격 요약 배치 조회용).
    // change1dPct는 포트폴리오 총 평가액의 전일 대비 등락 계산에 쓰인다(getGradeChart와 동일하게 market을 거슬러 올라가는 방식).
    @Query("SELECT cp.variant.id AS variantId, cp.market AS market, cp.currency AS currency, cp.change1dPct AS change1dPct "
            + "FROM CardPrice cp "
            + "WHERE cp.variant.id IN :variantIds AND cp.priceType = :priceType AND cp.grade = :grade AND cp.company = :company")
    List<VariantMarketPriceView> findMarketPricesByVariantIds(
            @Param("variantIds") List<Long> variantIds,
            @Param("priceType") String priceType,
            @Param("grade") String grade,
            @Param("company") String company);

    interface VariantMarketPriceView {
        Long getVariantId();

        BigDecimal getMarket();

        String getCurrency();

        BigDecimal getChange1dPct();
    }
}
