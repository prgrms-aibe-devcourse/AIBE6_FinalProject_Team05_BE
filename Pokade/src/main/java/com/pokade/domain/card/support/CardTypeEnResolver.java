package com.pokade.domain.card.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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

    // JAPANESE_TO_ENGLISH를 그대로 뒤집은 표준명 -> 원본(일본어) 텍스트 목록.
    private static final Map<String, List<String>> ENGLISH_TO_ORIGINALS = buildReverseMap(JAPANESE_TO_ENGLISH);

    private CardTypeEnResolver() {
    }

    private static Map<String, List<String>> buildReverseMap(Map<String, String> forward) {
        Map<String, List<String>> reverse = new HashMap<>();
        forward.forEach((original, standard) -> reverse.computeIfAbsent(standard, k -> new ArrayList<>()).add(original));
        return reverse;
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
                .filter(Objects::nonNull)
                .map(type -> JAPANESE_TO_ENGLISH.getOrDefault(type, type))
                .toList();
    }

    /**
     * 표준(영문) 타입명을 검색 필터로 받았을 때, DB의 원본 컬럼(다국어 텍스트)과 비교할 수 있도록
     * 그 표준명에 대응하는 모든 원본 텍스트 후보로 확장한다. 표준명 자체(영문 카드의 raw 값)도 항상
     * 포함하고, 매핑에 없는 값(신규/알 수 없는 값)은 원본 값 그대로 포함한다 - 필터가 빈 결과로
     * 좁아지지 않도록 방어.
     */
    public static List<String> resolveOriginalValues(List<String> standardTypes) {
        if (standardTypes == null) {
            return null;
        }
        return standardTypes.stream()
                .filter(Objects::nonNull)
                .flatMap(type -> Stream.concat(Stream.of(type), ENGLISH_TO_ORIGINALS.getOrDefault(type, List.of()).stream()))
                .distinct()
                .toList();
    }
}
