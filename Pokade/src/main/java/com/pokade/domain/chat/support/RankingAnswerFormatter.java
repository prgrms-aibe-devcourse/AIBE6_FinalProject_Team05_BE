package com.pokade.domain.chat.support;

import com.pokade.domain.price.dto.PriceRankingResponse;

import java.util.List;
import java.util.stream.Collectors;

// 랭킹 조회 결과를 챗봇 답변 문자열로 포맷한다.
// PriceChatTools(LLM tool 경로)와 ChatService의 프리셋 DB 직접 조회 경로가 동일한 포맷을 공유해서
// 두 경로의 응답 형식이 어긋나지 않도록 한다.
public final class RankingAnswerFormatter {

    public static final String EMPTY_MESSAGE = "현재 등락률을 계산할 수 있는 카드가 없습니다.";
    public static final String INVALID_TYPE_MESSAGE = "잘못된 랭킹 타입입니다. rise 또는 fall만 가능합니다.";

    private RankingAnswerFormatter() {
    }

    public static String format(List<PriceRankingResponse> ranking) {
        if (ranking.isEmpty()) {
            return EMPTY_MESSAGE;
        }
        return ranking.stream()
                .map(RankingAnswerFormatter::formatEntry)
                .collect(Collectors.joining("\n"));
    }

    // 카드명/가격/변동률/변동액만 노출 - cardId, imageUrl은 챗봇 답변에 불필요해서 제외
    private static String formatEntry(PriceRankingResponse r) {
        String rateSign = r.changeRate().signum() >= 0 ? "+" : "";
        String rateStr = r.changeRate().stripTrailingZeros().toPlainString();
        String amountSign = r.changeAmount() >= 0 ? "+" : "";
        return "%s / %,d원 / %s%s%% / %s%,d원".formatted(
                r.cardName(), r.price(), rateSign, rateStr, amountSign, r.changeAmount());
    }
}
