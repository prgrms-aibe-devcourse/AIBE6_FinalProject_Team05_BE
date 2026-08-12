package com.pokade.domain.card.support;

import java.util.regex.Pattern;

public final class KoreanTextUtil {

    private static final Pattern KOREAN_SYLLABLE = Pattern.compile("[가-힣]");
    private static final Pattern CHOSUNG_ONLY = Pattern.compile("^[ㄱ-ㅎ]+$");

    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /** 한글 완성형 음절 시작 코드포인트, '가'. */
    private static final int HANGUL_SYLLABLE_START = 0xAC00;
    /** 한글 완성형 음절 끝 코드포인트, '힣'. */
    private static final int HANGUL_SYLLABLE_END = 0xD7A3;
    /** 중성 21개 × 종성 28개 조합 수 - 초성 하나당 이 개수만큼의 음절이 대응됨. */
    private static final int JUNGSUNG_JONGSUNG_COUNT = 21 * 28;

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
            if (c >= HANGUL_SYLLABLE_START && c <= HANGUL_SYLLABLE_END) {
                int chosungIndex = (c - HANGUL_SYLLABLE_START) / JUNGSUNG_JONGSUNG_COUNT;
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
