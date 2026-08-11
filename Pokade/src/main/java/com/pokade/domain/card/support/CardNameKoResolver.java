package com.pokade.domain.card.support;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.pokade.domain.card.entity.Card;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CardNameKoResolver {

    private static final Pattern PURE_ENGLISH = Pattern.compile("^[A-Za-z0-9\\s-]+$");
    private static final Pattern TRAILING_ENGLISH_SUFFIX = Pattern.compile("[A-Za-z0-9-]+$");

    private final PokedexKoNameCache pokedexKoNameCache;

    public String resolve(Card card) {
        return resolve(card.getName(), card.getNationalPokedexNumbers());
    }

    /**
     * 카드 이름에서 종 이름 부분을 한글(nameKo)로 치환한다. pokedexNumbers가 비어있으면
     * (트레이너/에너지 카드) null을 반환한다. 복수 종 조합 카드는 흔치 않으므로 첫 번째
     * 도감번호만 사용한다. 매핑 자체가 없으면 null을 반환한다 - 어설프게 틀린 한글 이름을
     * 보여주는 것보다 안전하다.
     *
     * 카드 이름이 순수 영문(알파벳/숫자/공백/하이픈)이면 nameEn을 대소문자까지 정확히
     * 일치하는 부분 문자열로 찾아 nameKo로 치환한다(예: "Charizard ex" → "리자몽 ex").
     * 카드 이름에 비영문 문자(일본어 등)가 하나라도 있으면 nameEn 부분일치는 의미가 없으므로,
     * 대신 문자열 끝의 연속된 영문/숫자/하이픈 구간만 접미사로 떼어내고 나머지 전체를
     * nameKo로 치환한다(예: "クヌギダマex" → "파이코ex", "クヌギダマ" → "파이코").
     */
    public String resolve(String cardName, List<Integer> pokedexNumbers) {
        if (cardName == null || pokedexNumbers == null || pokedexNumbers.isEmpty()) {
            return null;
        }
        Integer pokedexNumber = pokedexNumbers.get(0);
        String nameEn = pokedexKoNameCache.getNameEn(pokedexNumber);
        String nameKo = pokedexKoNameCache.getNameKo(pokedexNumber);
        if (nameEn == null || nameKo == null) {
            return null;
        }
        if (PURE_ENGLISH.matcher(cardName).matches()) {
            return cardName.contains(nameEn) ? cardName.replace(nameEn, nameKo) : null;
        }
        Matcher suffixMatcher = TRAILING_ENGLISH_SUFFIX.matcher(cardName);
        String suffix = suffixMatcher.find() ? suffixMatcher.group() : "";
        return nameKo + suffix;
    }
}
