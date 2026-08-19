package com.pokade.domain.trade.dto;

import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MyTradeSearchCondition(
        TradeRole role,
        List<TradeStatus> statuses,
        LocalDate from,
        LocalDate to
) {

    private static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    public MyTradeSearchCondition {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        if (to != null && to.isAfter(MAX_DATE)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
    }

    public boolean includeBuy() {
        return role != TradeRole.SELL;
    }

    public boolean includeSell() {
        return role != TradeRole.BUY;
    }

    // IN 절에는 null을 넘길 수 없어 미지정이면 전체 상태로 채운다
    public List<TradeStatus> statusesOrAll() {
        return (statuses == null || statuses.isEmpty()) ? List.of(TradeStatus.values()) : statuses;
    }

    // 날짜 미지정 시 null을 넘기면 Postgres가 파라미터 타입을 추론하지 못해 센티널 값으로 채운다
    public LocalDateTime fromDateTime() {
        return (from == null ? MIN_DATE : from).atStartOfDay();
    }

    // to 당일 거래까지 포함되도록 다음 날 0시 미만으로 비교한다
    public LocalDateTime toDateTimeExclusive() {
        return (to == null ? MAX_DATE : to).plusDays(1).atStartOfDay();
    }
}
