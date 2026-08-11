package com.pokade.domain.chat.tool;

import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.service.CardService;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.dto.PriceStatsResponse;
import com.pokade.domain.price.dto.PriceSummaryResponse;
import com.pokade.domain.price.dto.TradeSummaryResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

// LLM이 시세 질문에 답할 때 호출하는 도구 모음 - 여기서 반환하는 문자열만 답변의 사실 근거가 됨(그 외 정보는 LLM이 임의 생성하지 않도록 시스템 프롬프트로 제약)
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceChatTools {

    private static final int SEARCH_LIMIT = 5;

    // 이번 요청(스레드)에서 실제 시세 데이터 tool이 호출됐는지 추적 - ChatService가 "tool 근거 없이 답했는지" 검증할 때 사용.
    // Spring AI의 기본(동기) tool 실행은 같은 요청 스레드에서 이뤄지므로 ThreadLocal로 충분하다.
    private static final ThreadLocal<Boolean> PRICE_TOOL_INVOKED = ThreadLocal.withInitial(() -> false);

    private final PriceService priceService;
    private final CardService cardService;

    public static void resetInvocationTracking() {
        PRICE_TOOL_INVOKED.set(false);
    }

    public static boolean wasPriceToolInvoked() {
        return PRICE_TOOL_INVOKED.get();
    }

    @Tool(description = "카드 이름으로 카드를 검색해 cardId와 기본 정보를 반환한다. " +
            "다른 시세 조회 도구는 cardId가 필요하므로, 사용자가 카드 이름으로 물어보면 먼저 이 도구로 cardId를 찾아야 한다.")
    public String searchCard(@ToolParam(description = "검색할 카드 이름(예: 피카츄)") String keyword) {
        try {
            Page<CardResponse> result = cardService.searchByKeyword(keyword, PageRequest.of(0, SEARCH_LIMIT));
            if (result.isEmpty()) {
                return "'" + keyword + "'로 검색된 카드가 없습니다.";
            }
            return result.getContent().stream()
                    .map(c -> "cardId=%d, name=%s, set=%s, rarity=%s".formatted(c.id(), c.name(), c.setName(), c.rarity()))
                    .collect(Collectors.joining("\n"));
        } catch (BusinessException e) {
            log.warn("챗봇 카드 검색 실패: keyword={}", keyword, e);
            return "카드 검색 중 오류가 발생했습니다.";
        }
    }

    @Tool(description = "cardId로 현재 매물 최저가(buyPrice)와 매수 희망 최고가(sellPrice)를 조회한다.")
    public String getCurrentPrice(@ToolParam(description = "조회할 카드의 cardId") Long cardId) {
        PRICE_TOOL_INVOKED.set(true);
        try {
            PriceSummaryResponse summary = priceService.getSummary(cardId, null);
            if (summary.buyPrice() == null && summary.sellPrice() == null) {
                return "이 카드는 현재 등록된 매물/매수 희망가가 없습니다.";
            }
            return "현재 최저 판매가(buyPrice)=%s원, 최고 매수 희망가(sellPrice)=%s원 (%s)".formatted(
                    summary.buyPrice(), summary.sellPrice(), summary.currency());
        } catch (BusinessException e) {
            return "해당 cardId의 시세 정보를 찾을 수 없습니다.";
        }
    }

    @Tool(description = "cardId로 최근 7일 대비 이전 7일의 가격 변동률(%), 변동액, 거래량을 조회한다. " +
            "\"얼마나 올랐어/내렸어\" 같은 질문에 사용한다.")
    public String getPriceStats(@ToolParam(description = "조회할 카드의 cardId") Long cardId) {
        PRICE_TOOL_INVOKED.set(true);
        try {
            PriceStatsResponse stats = priceService.getStats(cardId, null);
            return "최근 7일 대비 이전 7일 변동률=%s%%, 변동액=%d원, 최근 7일 거래량=%d건".formatted(
                    stats.changeRate(), stats.changeAmount(), stats.volume());
        } catch (BusinessException e) {
            return "해당 cardId의 변동률 정보를 찾을 수 없습니다.";
        }
    }

    @Tool(description = "cardId로 최근 체결된 거래 내역(최대 20건)을 시간순으로 조회한다.")
    public String getRecentTrades(@ToolParam(description = "조회할 카드의 cardId") Long cardId) {
        PRICE_TOOL_INVOKED.set(true);
        try {
            var trades = priceService.getRecentTrades(cardId);
            if (trades.isEmpty()) {
                return "이 카드는 최근 체결된 거래가 없습니다.";
            }
            return trades.stream()
                    .map(TradeSummaryResponse::toString)
                    .collect(Collectors.joining("\n"));
        } catch (BusinessException e) {
            return "해당 cardId의 거래 내역을 찾을 수 없습니다.";
        }
    }

    @Tool(description = "전체 카드 중 최근 7일 대비 이전 7일 등락률 상위 10개 카드를 조회한다. " +
            "type은 \"rise\"(급등) 또는 \"fall\"(급락)만 가능하다. \"오늘 급등/급락한 카드\" 질문에 사용한다. " +
            "결과에 이미지 URL은 없다 - 답변에 이미지나 링크를 넣지 말고 카드명/가격/변동률/변동액만 그대로 전달하라.")
    public String getRanking(@ToolParam(description = "rise 또는 fall") String type) {
        PRICE_TOOL_INVOKED.set(true);
        try {
            var ranking = priceService.getRanking(type);
            if (ranking.isEmpty()) {
                return "현재 등락률을 계산할 수 있는 카드가 없습니다.";
            }
            return ranking.stream()
                    .map(this::formatRankingEntry)
                    .collect(Collectors.joining("\n"));
        } catch (BusinessException e) {
            return "잘못된 랭킹 타입입니다. rise 또는 fall만 가능합니다.";
        }
    }

    // 카드명/가격/변동률/변동액만 노출 - cardId, imageUrl은 챗봇 답변에 불필요해서 제외(LLM이 마크다운 이미지를 붙이는 걸 원천적으로 막는 효과도 있음)
    private String formatRankingEntry(PriceRankingResponse r) {
        return "카드명=%s, 가격=%d원, 변동률=%s%%, 변동액=%d원".formatted(
                r.cardName(), r.price(), r.changeRate(), r.changeAmount());
    }
}
