package com.pokade.domain.card.support;

import java.util.regex.Pattern;

public final class KoreanTextUtil {

    private static final Pattern KOREAN_SYLLABLE = Pattern.compile("[가-힣]");
    private static final Pattern CHOSUNG_ONLY = Pattern.compile("^[ㄱ-ㅎ]+$");

    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private KoreanTextUtil() {
    }

    public static boolean isKorean(String text) {
        return text != null && KOREAN_SYLLABLE.matcher(text).find();
    }

    /** 완성형 한글 음절을 초성 자음 하나로 변환한다. 음절이 아닌 문자(자음/특수문자 등)는 그대로 둔다. */
    public static String extractChosung(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int chosungIndex = (c - 0xAC00) / (21 * 28);
                result.append(CHOSUNG[chosungIndex]);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static boolean isChosungOnly(String text) {
        return text != null && CHOSUNG_ONLY.matcher(text).matches();
    }
}
