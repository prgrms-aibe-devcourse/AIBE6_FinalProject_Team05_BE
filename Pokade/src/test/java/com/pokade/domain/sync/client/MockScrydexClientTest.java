package com.pokade.domain.sync.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.CardPriceDto;
import com.pokade.domain.sync.client.dto.CardVariantDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;

class MockScrydexClientTest {

    private final MockScrydexClient client = new MockScrydexClient();

    @Test
    @DisplayName("t1 data.sql과 동일한 8개의 세트를 반환한다")
    void t1() {
        List<ExpansionDto> expansions = client.fetchExpansions();

        assertThat(expansions).hasSize(8);
        assertThat(expansions)
                .filteredOn(e -> e.id().equals("base1"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.name()).isEqualTo("Base");
                    assertThat(e.total()).isEqualTo(102);
                });
    }

    @Test
    @DisplayName("t2 세트 ID로 조회하면 해당 세트에 속한 카드만 반환한다")
    void t2() {
        List<CardDto> cards = client.fetchCards("base1");

        assertThat(cards)
                .extracting(CardDto::externalId)
                .containsExactlyInAnyOrder("base1-4", "base1-2", "base1-58");
        assertThat(cards).allMatch(c -> c.expansionId().equals("base1"));
    }

    @Test
    @DisplayName("t3 존재하지 않는 세트 ID로 조회하면 빈 목록을 반환한다")
    void t3() {
        List<CardDto> cards = client.fetchCards("no-such-expansion");

        assertThat(cards).isEmpty();
    }

    @Test
    @DisplayName("t4 카드 ID로 조회하면 대표 변형 1개를 반환한다")
    void t4() {
        List<CardVariantDto> variants = client.fetchCardVariants("base1-4");

        assertThat(variants).singleElement().satisfies(v -> {
            assertThat(v.variantName()).isEqualTo("unlimitedHolofoil");
            assertThat(v.primary()).isTrue();
            assertThat(v.cardExternalId()).isEqualTo("base1-4");
        });
    }

    @Test
    @DisplayName("t5 변형 ID로 조회하면 PSA 10등급/9등급 시세 2건을 반환한다")
    void t5() {
        CardVariantDto variant = client.fetchCardVariants("base1-4").get(0);

        List<CardPriceDto> prices = client.fetchCardPrices(variant.variantId());

        assertThat(prices)
                .extracting(CardPriceDto::grade)
                .containsExactlyInAnyOrder("10", "9");
        assertThat(prices).allMatch(p -> p.company().equals("PSA") && p.priceType().equals("graded"));
    }
}
