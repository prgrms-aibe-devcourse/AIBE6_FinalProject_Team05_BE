package com.pokade.domain.card.support;

import java.util.regex.Pattern;

public final class KoreanTextUtil {

    private static final Pattern KOREAN_SYLLABLE = Pattern.compile("[가-힣]");

    private KoreanTextUtil() {
    }

    public static boolean isKorean(String text) {
        return text != null && KOREAN_SYLLABLE.matcher(text).find();
    }
}
