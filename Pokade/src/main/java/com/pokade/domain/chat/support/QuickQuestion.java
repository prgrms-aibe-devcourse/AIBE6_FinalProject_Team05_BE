package com.pokade.domain.chat.support;

import com.pokade.domain.chat.dto.QuickQuestionResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// FAQ 버튼용 프리셋 질문 - 프론트가 그대로 버튼 라벨/질문으로 사용
public enum QuickQuestion {

    // rankingType이 있는 프리셋(급등/급락)은 파라미터 해석이 필요 없는 고정 DB 조회 하나로 답이 결정되므로
    // ChatService에서 LLM 호출 없이 PriceService.getRanking(rankingType)을 직접 불러 답한다(토큰 절약).
    TOP_GAINERS("top-gainers", "최근 7일 급등한 카드", "최근 7일 기준 가장 많이 오른 카드 알려줘", "rise"),
    TOP_LOSERS("top-losers", "최근 7일 급락한 카드", "최근 7일 기준 가장 많이 내린 카드 알려줘", "fall"),
    HOW_TO_USE("how-to-use", "챗봇 사용법", "이 챗봇으로 어떤 걸 물어볼 수 있어?", null);

    private final String id;
    private final String label;
    private final String question;
    private final String rankingType;

    QuickQuestion(String id, String label, String question, String rankingType) {
        this.id = id;
        this.label = label;
        this.question = question;
        this.rankingType = rankingType;
    }

    public QuickQuestionResponse toResponse() {
        return new QuickQuestionResponse(id, label, question);
    }

    public String id() {
        return id;
    }

    public String question() {
        return question;
    }

    public String rankingType() {
        return rankingType;
    }

    public static List<QuickQuestionResponse> all() {
        return Arrays.stream(values()).map(QuickQuestion::toResponse).toList();
    }

    // 비로그인 사용자는 이 프리셋 질문과 정확히 일치하는 경우에만 질의 가능(자유 입력 차단)
    public static boolean isPreset(String message) {
        return Arrays.stream(values()).anyMatch(q -> q.question.equals(message));
    }

    // message가 랭킹 프리셋(급등/급락)과 정확히 일치하면 PriceService.getRanking에 넘길 type("rise"/"fall")을 반환한다.
    public static Optional<String> rankingTypeFor(String message) {
        return Arrays.stream(values())
                .filter(q -> q.rankingType != null && q.question.equals(message))
                .map(q -> q.rankingType)
                .findFirst();
    }

    // id(버튼 id)로 랭킹 프리셋(급등/급락)을 찾는다 - 히스토리 이관(ChatService.importHistory)에서 사용.
    // rankingType이 없는 프리셋(HOW_TO_USE)은 재계산할 DB 값이 없으므로 대상에서 제외한다.
    public static Optional<QuickQuestion> findRankingPresetById(String id) {
        return Arrays.stream(values())
                .filter(q -> q.rankingType != null && q.id.equals(id))
                .findFirst();
    }
}
