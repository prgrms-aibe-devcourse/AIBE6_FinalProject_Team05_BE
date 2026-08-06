package com.pokade.domain.price;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

public enum RankingType {
    RISE("rise"),
    FALL("fall");

    private final String code;

    RankingType(String code) {
        this.code = code;
    }

    public static RankingType from(String code) {
        for (RankingType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_RANKING_TYPE);
    }
}
