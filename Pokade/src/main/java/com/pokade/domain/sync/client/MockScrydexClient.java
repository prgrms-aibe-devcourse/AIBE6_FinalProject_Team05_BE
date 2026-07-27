package com.pokade.domain.sync.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.CardPriceDto;
import com.pokade.domain.sync.client.dto.CardVariantDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;

/**
 * data.sql에 시딩된 카드 목데이터와 동일한 형태를 반환하는 ScrydexClient 구현체.
 * 실제 HTTP 호출 없이 고정된 더미 데이터만 반환한다.
 */
@Component
public class MockScrydexClient implements ScrydexClient {

    private record PriceFixture(String grade, BigDecimal low, BigDecimal mid, BigDecimal high,
                                 BigDecimal market, String currency) {
    }

    private record CardFixture(String externalId, String name, String setName, String rarity, String supertype,
                                List<String> types, String printedNumber, String expansionId,
                                String variantName, List<PriceFixture> prices) {
    }

    private static final List<ExpansionDto> EXPANSIONS = List.of(
            new ExpansionDto("base1", "Base", "Base", null, 102, "EN", LocalDate.of(1999, 1, 9)),
            new ExpansionDto("sv3pt5", "151", "Scarlet & Violet", null, 207, "EN", LocalDate.of(2023, 6, 16)),
            new ExpansionDto("zsv10pt5", "Black Bolt", "Scarlet & Violet", null, 172, "EN", LocalDate.of(2025, 7, 18)),
            new ExpansionDto("sm11", "Unified Minds", "Sun & Moon", null, 260, "EN", LocalDate.of(2019, 8, 2)),
            new ExpansionDto("xy7", "Ancient Origins", "XY", null, 98, "EN", LocalDate.of(2015, 8, 12)),
            new ExpansionDto("sm3", "Burning Shadows", "Sun & Moon", null, 168, "EN", LocalDate.of(2017, 8, 4)),
            new ExpansionDto("me1", "Mega Evolution", "Mega Evolution", null, 188, "EN", LocalDate.of(2025, 9, 26)),
            new ExpansionDto("sv10_ja", "サンダー", "Scarlet & Violet", null, 98, "JA", LocalDate.of(2024, 11, 1))
    );

    private static final List<CardFixture> CARDS = List.of(
            new CardFixture("base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                    List.of("Fire"), "4/102", "base1", "unlimitedHolofoil",
                    prices("2350.0", "2566.0", "2650.0", "2567.88", "780.0", "845.0", "910.0", "848.67", "USD")),
            new CardFixture("base1-2", "Blastoise", "Base", "Rare Holo", "Pokémon",
                    List.of("Water"), "2/102", "base1", "unlimitedHolofoil",
                    prices("720.0", "760.0", "810.0", "768.4", "610.0", "655.0", "700.0", "648.0", "USD")),
            new CardFixture("base1-58", "Pikachu", "Base", "Common", "Pokémon",
                    List.of("Lightning"), "58/102", "base1", "unlimited",
                    prices("210.0", "235.0", "260.0", "238.4", "60.0", "68.0", "75.0", "69.2", "USD")),
            new CardFixture("sv3pt5-6", "Charizard ex", "151", "Double Rare", "Pokémon",
                    List.of("Fire"), "006/165", "sv3pt5", "holofoil",
                    prices("320.0", "340.0", "365.0", "342.5", "165.0", "178.0", "190.0", "180.2", "USD")),
            new CardFixture("sv3pt5-54", "Blastoise ex", "151", "Double Rare", "Pokémon",
                    List.of("Water"), "054/165", "sv3pt5", "holofoil",
                    prices("140.0", "150.0", "162.0", "151.8", "70.0", "76.0", "82.0", "77.1", "USD")),
            new CardFixture("sv3pt5-25", "Pikachu", "151", "Common", "Pokémon",
                    List.of("Lightning"), "025/165", "sv3pt5", "normal",
                    prices("22.0", "25.0", "28.0", "25.6", "8.0", "9.2", "10.5", "9.4", "USD")),
            new CardFixture("zsv10pt5-105", "Seismitoad", "Black Bolt", "Illustration Rare", "Pokémon",
                    List.of("Water"), "105/086", "zsv10pt5", "holofoil",
                    prices("2200.0", "2350.0", "2450.0", "2399.0", "780.0", "820.0", "860.0", "822.4", "USD")),
            new CardFixture("sm11-95", "Alakazam GX", "Unified Minds", "Rare Holo GX", "Pokémon",
                    List.of("Psychic"), "95/236", "sm11", "holofoil",
                    prices("24.0", "27.0", "30.0", "27.4", "10.0", "11.5", "13.0", "11.8", "USD")),
            new CardFixture("xy7-54", "Gardevoir-EX", "Ancient Origins", "Rare Holo EX", "Pokémon",
                    List.of("Fairy"), "54/98", "xy7", "holofoil",
                    prices("68.0", "74.0", "80.0", "75.2", "28.0", "31.0", "34.0", "31.6", "USD")),
            new CardFixture("sm3-20", "Charizard-GX", "Burning Shadows", "Rare Holo GX", "Pokémon",
                    List.of("Fire"), "20/147", "sm3", "holofoil",
                    prices("210.0", "225.0", "240.0", "228.0", "88.0", "95.0", "102.0", "96.4", "USD")),
            new CardFixture("me1-12", "Mega Lucario ex", "Mega Evolution", "Double Rare", "Pokémon",
                    List.of("Fighting"), "012/132", "me1", "holofoil",
                    prices("46.0", "50.0", "55.0", "51.2", "20.0", "22.5", "25.0", "22.9", "USD")),
            new CardFixture("sv10_ja-1", "クヌギダマ", "サンダー", "通常", "ポケモン",
                    List.of("草"), "001/098", "sv10_ja", "normal",
                    prices("5.0", "5.6", "6.2", "5.7", "2.0", "2.3", "2.6", "2.35", "JPY"))
    );

    private static List<PriceFixture> prices(String grade10Low, String grade10Mid, String grade10High,
                                              String grade10Market, String grade9Low, String grade9Mid,
                                              String grade9High, String grade9Market, String currency) {
        return List.of(
                new PriceFixture("10", new BigDecimal(grade10Low), new BigDecimal(grade10Mid),
                        new BigDecimal(grade10High), new BigDecimal(grade10Market), currency),
                new PriceFixture("9", new BigDecimal(grade9Low), new BigDecimal(grade9Mid),
                        new BigDecimal(grade9High), new BigDecimal(grade9Market), currency)
        );
    }

    private static String variantId(String cardExternalId, String variantName) {
        return cardExternalId + "-" + variantName;
    }

    @Override
    public List<ExpansionDto> fetchExpansions() {
        return EXPANSIONS;
    }

    @Override
    public List<CardDto> fetchCards(String expansionId) {
        return CARDS.stream()
                .filter(c -> c.expansionId().equals(expansionId))
                .map(c -> new CardDto(c.externalId(), c.name(), c.setName(), c.rarity(), c.supertype(),
                        c.types(), c.printedNumber(), c.expansionId()))
                .toList();
    }

    @Override
    public List<CardVariantDto> fetchCardVariants(String cardId) {
        return CARDS.stream()
                .filter(c -> c.externalId().equals(cardId))
                .map(c -> new CardVariantDto(variantId(c.externalId(), c.variantName()), c.externalId(),
                        c.variantName(), true))
                .toList();
    }

    @Override
    public List<CardPriceDto> fetchCardPrices(String variantId) {
        return CARDS.stream()
                .filter(c -> variantId(c.externalId(), c.variantName()).equals(variantId))
                .findFirst()
                .map(c -> c.prices().stream()
                        .map(p -> new CardPriceDto(variantId, "graded", p.grade(), "PSA",
                                p.low(), p.mid(), p.high(), p.market(), p.currency()))
                        .toList())
                .orElse(List.of());
    }
}
