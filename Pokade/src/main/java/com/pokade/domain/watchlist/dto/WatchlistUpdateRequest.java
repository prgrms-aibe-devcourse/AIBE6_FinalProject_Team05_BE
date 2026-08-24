package com.pokade.domain.watchlist.dto;

import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * PATCH /api/watchlist/{id} 요청 바디.
 *
 * <p><b>null = "변경 없음"</b>이라는 기존 계약은 그대로다({@link Watchlist#updateTargetPrices}). 그 계약만으로는
 * "목표가를 지워 미설정으로 되돌리기"를 표현할 수 없어서(안 보내면 유지, 보내면 그 값으로 설정 — 지움에 해당하는
 * 입력이 없다) clear 플래그를 별도로 둔다. 0을 지움으로 쓰는 방식은 {@code @Positive}를 걷어내야 해 음수가
 * 뚫리고, 값 자체에 의미를 싣는 magic value가 되어 택하지 않았다.
 */
public record WatchlistUpdateRequest(
        @Positive(message = "targetBuyPrice는 0보다 커야 합니다.")
        @Max(value = Watchlist.MAX_TARGET_PRICE, message = "목표가는 1억원을 초과할 수 없습니다.")
        Integer targetBuyPrice,

        @Positive(message = "targetSellPrice는 0보다 커야 합니다.")
        @Max(value = Watchlist.MAX_TARGET_PRICE, message = "목표가는 1억원을 초과할 수 없습니다.")
        Integer targetSellPrice,

        // null/false 둘 다 "재알림 요청 없음"으로 취급 - Boolean.TRUE.equals()로 체크할 것(원시 boolean 언박싱 NPE 방지)
        Boolean resendNotification,

        // true면 목표 구매가를 null로 되돌린다. null/false는 "지우지 않음"이라 기존 요청과 동작이 같다.
        Boolean clearTargetBuyPrice,

        // true면 목표 판매가를 null로 되돌린다.
        Boolean clearTargetSellPrice
) {

    /**
     * 값과 clear 플래그를 함께 보내는 것은 해석할 여지가 있는 입력이 아니라 클라이언트 버그다
     * ("5000으로 설정하면서 동시에 지워라"). 어느 한쪽을 우선한다는 규칙을 만들면 그 규칙을 모르는
     * 호출부가 조용히 반대로 동작하므로, 애초에 거절해서 드러낸다.
     */
    public WatchlistUpdateRequest {
        rejectValueWithClear(targetBuyPrice, clearTargetBuyPrice, "targetBuyPrice");
        rejectValueWithClear(targetSellPrice, clearTargetSellPrice, "targetSellPrice");
    }

    /** 기존 3-인자 호출부(테스트 다수 포함)를 그대로 두기 위한 오버로드 - clear 플래그는 "지우지 않음"으로 채운다. */
    public WatchlistUpdateRequest(Integer targetBuyPrice, Integer targetSellPrice, Boolean resendNotification) {
        this(targetBuyPrice, targetSellPrice, resendNotification, null, null);
    }

    private static void rejectValueWithClear(Integer price, Boolean clear, String fieldName) {
        if (price != null && Boolean.TRUE.equals(clear)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    fieldName + "에 값과 삭제 요청을 함께 보낼 수 없습니다.");
        }
    }

    public boolean clearBuyPriceRequested() {
        return Boolean.TRUE.equals(clearTargetBuyPrice);
    }

    public boolean clearSellPriceRequested() {
        return Boolean.TRUE.equals(clearTargetSellPrice);
    }
}
