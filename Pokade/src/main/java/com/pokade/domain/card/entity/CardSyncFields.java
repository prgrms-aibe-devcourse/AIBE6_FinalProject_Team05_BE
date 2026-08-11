package com.pokade.domain.card.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scrydex 동기화 배치가 카드 1건을 upsert할 때 필요한 필드 묶음. {@link Card#applySync}가 그대로 반영하고,
 * {@link #toNewCard(String)}로 최초 동기화 시의 신규 {@link Card}도 동일한 값에서 만들 수 있다.
 */
public record CardSyncFields(
        String name,
        String setName,
        String rarity,
        String supertype,
        List<String> subtypes,
        List<String> types,
        List<String> evolvesFrom,
        String printedNumber,
        String rarityCode,
        String hp,
        String artist,
        List<Integer> nationalPokedexNumbers,
        String imageSmall,
        String imageMedium,
        String imageLarge,
        Expansion expansion,
        Integer expansionSortOrder,
        String languageCode,
        LocalDateTime syncedAt
) {

    public Card toNewCard(String externalId) {
        return Card.builder()
                .externalId(externalId)
                .name(name)
                .setName(setName)
                .rarity(rarity)
                .supertype(supertype)
                .subtypes(subtypes)
                .types(types)
                .evolvesFrom(evolvesFrom)
                .printedNumber(printedNumber)
                .rarityCode(rarityCode)
                .hp(hp)
                .artist(artist)
                .nationalPokedexNumbers(nationalPokedexNumbers)
                .imageSmall(imageSmall)
                .imageMedium(imageMedium)
                .imageLarge(imageLarge)
                .expansion(expansion)
                .expansionSortOrder(expansionSortOrder)
                .languageCode(languageCode)
                .syncedAt(syncedAt)
                .build();
    }
}
