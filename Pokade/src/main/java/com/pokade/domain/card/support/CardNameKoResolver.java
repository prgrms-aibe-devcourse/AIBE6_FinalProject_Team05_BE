package com.pokade.domain.card.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.pokade.domain.card.entity.Card;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CardNameKoResolver {

    private final PokedexKoNameCache pokedexKoNameCache;

    public String resolve(Card card) {
        return resolve(card.getName(), card.getNationalPokedexNumbers());
    }

    /**
     * 카드 이름에서 종 이름(nameEn) 부분을 찾아 한글(nameKo)로 치환한다. pokedexNumbers가
     * 비어있으면(트레이너/에너지 카드) null을 반환한다. 복수 종 조합 카드는 흔치 않으므로
     * 첫 번째 도감번호만 사용한다. nameEn이 카드 이름에 대소문자까지 정확히 일치하는 부분
     * 문자열로 없거나 매핑 자체가 없으면 null을 반환한다 - 어설프게 틀린 한글 이름을
     * 보여주는 것보다 안전하다.
     */
    public String resolve(String cardName, List<Integer> pokedexNumbers) {
        if (cardName == null || pokedexNumbers == null || pokedexNumbers.isEmpty()) {
            return null;
        }
        Integer pokedexNumber = pokedexNumbers.get(0);
        String nameEn = pokedexKoNameCache.getNameEn(pokedexNumber);
        String nameKo = pokedexKoNameCache.getNameKo(pokedexNumber);
        if (nameEn == null || nameKo == null || !cardName.contains(nameEn)) {
            return null;
        }
        return cardName.replace(nameEn, nameKo);
    }
}
