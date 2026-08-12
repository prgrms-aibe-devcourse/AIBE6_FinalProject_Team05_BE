package com.pokade.domain.card.support;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CardRarityResolver {

    private static final Map<String, String> RARITY_CODE_TO_LABEL = Map.ofEntries(
            Map.entry("●", "Common"),
            Map.entry("★H", "Rare Holo"),
            Map.entry("◇◇", "Double Rare"),
            Map.entry("☆1", "Illustration Rare"),
            Map.entry("EX", "Rare Holo EX"),
            Map.entry("GX", "Rare Holo GX")
    );

    // rarity_code는 원본 rarity 텍스트가 아니라 언어와 무관한 심볼이라 RARITY_CODE_TO_LABEL을
    // 그대로 뒤집을 수 없다(뒤집으면 코드가 나오는데, 필터가 비교하는 컬럼은 코드가 아니라
    // 원본 텍스트인 c.rarity이기 때문). 그래서 표준 라벨 -> 원본 텍스트 목록은 로컬 DB에서
    // 실제로 확인된 교차언어 사례만 별도로 채운다(2026-08-12 기준: "●" 코드가 EN "Common"/
    // JA "通常" 두 텍스트를 가짐을 실측 확인). 나머지 코드는 JA 대응 텍스트가 아직 확인되지 않아
    // 표준명 자기 자신만 후보가 된다(resolveOriginalValues에서 기본 포함).
    private static final Map<String, List<String>> LABEL_TO_KNOWN_ORIGINAL_TEXTS = Map.of(
            "Common", List.of("通常")
    );

    private CardRarityResolver() {
    }

    /**
     * rarity_code를 표준(영문) 레어도 명칭으로 치환한다. 매핑에 없는 코드(신규 코드거나 code가 null)는
     * 원본 rarity 값 그대로 반환한다 - 신규 코드가 추가돼도 서비스가 죽지 않아야 하기 때문.
     */
    public static String resolve(String rarityCode, String rarity) {
        if (rarityCode == null) {
            return rarity;
        }
        return RARITY_CODE_TO_LABEL.getOrDefault(rarityCode, rarity);
    }

    /**
     * 표준(영문) 레어도 명칭을 검색 필터로 받았을 때, DB의 원본 rarity 텍스트 컬럼과 비교할 수 있도록
     * 그 표준명에 대응하는 원본 텍스트 후보로 확장한다. 표준명 자체(영문 카드의 raw 값)는 항상 포함하고,
     * 알려진 다국어 원본 텍스트가 있으면 추가한다 - 알려지지 않은 값은 표준명 그대로만 포함되므로
     * 필터가 빈 결과로 좁아지지 않는다.
     */
    public static List<String> resolveOriginalValues(List<String> standardRarities) {
        if (standardRarities == null) {
            return null;
        }
        return standardRarities.stream()
                .flatMap(label -> Stream.concat(Stream.of(label), LABEL_TO_KNOWN_ORIGINAL_TEXTS.getOrDefault(label, List.of()).stream()))
                .distinct()
                .toList();
    }
}
