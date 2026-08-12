package com.pokade.domain.chat.support;

import com.pokade.domain.chat.dto.QuickQuestionResponse;

import java.util.Arrays;
import java.util.List;

// FAQ 버튼용 프리셋 질문 - 프론트가 그대로 버튼 라벨/질문으로 사용
public enum QuickQuestion {

    TOP_GAINERS("top-gainers", "최근 7일 급등한 카드", "최근 7일 기준 가장 많이 오른 카드 알려줘"),
    TOP_LOSERS("top-losers", "최근 7일 급락한 카드", "최근 7일 기준 가장 많이 내린 카드 알려줘"),
    HOW_TO_USE("how-to-use", "챗봇 사용법", "이 챗봇으로 어떤 걸 물어볼 수 있어?");

    private final String id;
    private final String label;
    private final String question;

    QuickQuestion(String id, String label, String question) {
        this.id = id;
        this.label = label;
        this.question = question;
    }

    public QuickQuestionResponse toResponse() {
        return new QuickQuestionResponse(id, label, question);
    }

    public static List<QuickQuestionResponse> all() {
        return Arrays.stream(values()).map(QuickQuestion::toResponse).toList();
    }

    // 비로그인 사용자는 이 프리셋 질문과 정확히 일치하는 경우에만 질의 가능(자유 입력 차단)
    public static boolean isPreset(String message) {
        return Arrays.stream(values()).anyMatch(q -> q.question.equals(message));
    }
}
