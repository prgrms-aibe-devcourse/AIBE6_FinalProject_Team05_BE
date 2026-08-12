package com.pokade.domain.card.support;

import java.util.Map;

public final class CardRarityResolver {

    private static final Map<String, String> RARITY_CODE_TO_LABEL = Map.ofEntries(
            Map.entry("●", "Common"),
            Map.entry("★H", "Rare Holo"),
            Map.entry("◇◇", "Double Rare"),
            Map.entry("☆1", "Illustration Rare"),
            Map.entry("EX", "Rare Holo EX"),
            Map.entry("GX", "Rare Holo GX")
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
}
