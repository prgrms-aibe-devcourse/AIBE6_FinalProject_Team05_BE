package com.pokade.domain.card.support;

import java.util.List;
import java.util.Map;

public final class CardTypeEnResolver {

    private static final Map<String, String> JAPANESE_TO_ENGLISH = Map.ofEntries(
            Map.entry("草", "Grass"),
            Map.entry("炎", "Fire"),
            Map.entry("水", "Water"),
            Map.entry("雷", "Lightning"),
            Map.entry("超", "Psychic"),
            Map.entry("闘", "Fighting"),
            Map.entry("悪", "Darkness"),
            Map.entry("鋼", "Metal"),
            Map.entry("フェアリー", "Fairy"),
            Map.entry("ドラゴン", "Dragon"),
            Map.entry("無色", "Colorless")
    );

    private CardTypeEnResolver() {
    }

    /**
     * 일본어 타입명을 영문 타입명으로 치환한다. 매핑에 없는 값(이미 영문이거나 알 수 없는 값)은
     * 원본 그대로 유지한다 - 신규 타입이 추가돼도 서비스가 죽지 않아야 하기 때문.
     */
    public static List<String> resolve(List<String> types) {
        if (types == null) {
            return null;
        }
        return types.stream()
                .map(type -> JAPANESE_TO_ENGLISH.getOrDefault(type, type))
                .toList();
    }
}
