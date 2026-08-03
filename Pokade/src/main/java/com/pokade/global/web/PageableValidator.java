package com.pokade.global.web;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.springframework.data.domain.Pageable;

// 페이징 API의 size 상한 검증 — 도메인마다 자체 MAX_PAGE_SIZE를 두되, 검증/예외 로직만 공용화한다.
public final class PageableValidator {

    private PageableValidator() {
    }

    public static void validatePageSize(Pageable pageable, int maxPageSize) {
        if (pageable.getPageSize() > maxPageSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size는 최대 " + maxPageSize + "까지 요청할 수 있습니다.");
        }
    }
}
