package com.pokade.domain.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokade.domain.card.dto.CardFacetsResponse;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.ExpansionRepository;

@ExtendWith(MockitoExtension.class)
class CardFacetServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private ExpansionRepository expansionRepository;

    @InjectMocks
    private CardFacetService cardFacetService;

    @Test
    @DisplayName("t48 매핑에 없는 rarity_code는 원본 rarity 텍스트로 폴백해서 노출된다")
    void t48() {
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of(rarityView("ZZ", "Special Art Rare")));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.rarities())
                .extracting(CardFacetsResponse.FacetOption::value)
                .containsExactly("Special Art Rare");
    }

    @Test
    @DisplayName("t49 rarity_code가 null인 카드도 원본 rarity 텍스트로 Facet에 노출된다")
    void t49() {
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of(rarityView(null, "プロモ")));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.rarities())
                .extracting(CardFacetsResponse.FacetOption::value)
                .containsExactly("プロモ");
    }

    @Test
    @DisplayName("t50 name이 null인 expansion이 있어도 NPE 없이 빈 문자열로 노출된다")
    void t50() {
        Expansion expansion = Expansion.builder().id("legacy1").name(null).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(expansion));

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.expansions()).hasSize(1);
        assertThat(result.expansions().get(0).id()).isEqualTo("legacy1");
        assertThat(result.expansions().get(0).name()).isEqualTo("");
    }

    @Test
    @DisplayName("t51 name이 null인 expansion과 정상 expansion이 섞여 있어도 나머지는 이름순 정렬이 유지된다")
    void t51() {
        Expansion legacy = Expansion.builder().id("legacy1").name(null).syncedAt(LocalDateTime.now()).build();
        Expansion base = Expansion.builder().id("base1").name("Base").syncedAt(LocalDateTime.now()).build();
        Expansion swsh = Expansion.builder().id("swsh1").name("Sword & Shield").syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(swsh, legacy, base));

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.expansions())
                .extracting(CardFacetsResponse.ExpansionFacet::name)
                .containsExactly("", "Base", "Sword & Shield");
    }

    @Test
    @DisplayName("t52 rarity_code와 rarity가 둘 다 null인 카드가 섞여 있어도 NPE 없이 나머지 rarity는 정상 노출된다")
    void t52() {
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of(
                rarityView(null, null),
                rarityView("C", "Common"),
                rarityView(null, "프로모")));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.rarities())
                .extracting(CardFacetsResponse.FacetOption::value)
                .containsExactlyInAnyOrder("Common", "프로모");
    }

    @Test
    @DisplayName("t59 서로 다른 원본 값이 같은 표준 레어도/타입으로 리졸브되면 카드 수가 합산된다(#263)")
    void t59() {
        given(cardRepository.findTypeCounts()).willReturn(List.of(
                typeCountView("草", 2L),
                typeCountView("Grass", 3L)));
        given(cardRepository.findRarityCounts()).willReturn(List.of(
                rarityView("●", "Common", 4L),
                rarityView("●", "通常", 6L)));
        given(expansionRepository.findAll()).willReturn(List.of());

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.types())
                .containsExactly(new CardFacetsResponse.FacetOption("Grass", 5L));
        assertThat(result.rarities())
                .containsExactly(new CardFacetsResponse.FacetOption("Common", 10L));
    }

    @Test
    @DisplayName("t53 series가 있는 expansion은 series 값이 그대로 Facet에 노출된다")
    void t53() {
        Expansion expansion = Expansion.builder().id("sv3pt5").name("151")
                .series("Scarlet & Violet").syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(expansion));

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.expansions().get(0).series()).isEqualTo("Scarlet & Violet");
    }

    @Test
    @DisplayName("t54 series가 null인 expansion은 \"기타\" 그룹으로 노출된다")
    void t54() {
        Expansion expansion = Expansion.builder().id("legacy1").name("Legacy")
                .series(null).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(expansion));

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.expansions().get(0).series()).isEqualTo("기타");
    }

    @Test
    @DisplayName("t55 series 그룹은 그룹 내 최신 release_date 기준 내림차순으로, 그룹 내에서는 이름순으로 정렬된다")
    void t55() {
        // Old Series의 최신 release_date(2016)보다 New Series의 release_date(2020)가 더 최신이므로
        // New Series 그룹 전체가 앞에 와야 한다.
        Expansion oldSeriesNewer = Expansion.builder().id("old2").name("Old B")
                .series("Old Series").releaseDate(LocalDate.of(2016, 1, 1)).syncedAt(LocalDateTime.now()).build();
        Expansion oldSeriesOlder = Expansion.builder().id("old1").name("Old A")
                .series("Old Series").releaseDate(LocalDate.of(2015, 1, 1)).syncedAt(LocalDateTime.now()).build();
        Expansion newSeries = Expansion.builder().id("new1").name("New A")
                .series("New Series").releaseDate(LocalDate.of(2020, 1, 1)).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(oldSeriesNewer, oldSeriesOlder, newSeries));

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.expansions())
                .extracting(CardFacetsResponse.ExpansionFacet::name)
                .containsExactly("New A", "Old A", "Old B");
    }

    @Test
    @DisplayName("t56 release_date가 전부 null인 series는 가장 오래된 것으로 취급돼 맨 뒤로 정렬된다")
    void t56() {
        Expansion noDateSeries = Expansion.builder().id("nodate1").name("No Date")
                .series("Unknown Timing").syncedAt(LocalDateTime.now()).build();
        Expansion datedSeries = Expansion.builder().id("dated1").name("Dated")
                .series("Dated Series").releaseDate(LocalDate.of(1999, 1, 1)).syncedAt(LocalDateTime.now()).build();
        given(cardRepository.findTypeCounts()).willReturn(List.of());
        given(cardRepository.findRarityCounts()).willReturn(List.of());
        given(expansionRepository.findAll()).willReturn(List.of(noDateSeries, datedSeries));

        CardFacetsResponse result = cardFacetService.getFacets();

        assertThat(result.expansions())
                .extracting(CardFacetsResponse.ExpansionFacet::name)
                .containsExactly("Dated", "No Date");
    }

    private CardRepository.CardRarityView rarityView(String rarityCode, String rarity) {
        return rarityView(rarityCode, rarity, 1L);
    }

    private CardRepository.CardRarityView rarityView(String rarityCode, String rarity, long count) {
        return new CardRepository.CardRarityView() {
            @Override
            public String getRarityCode() {
                return rarityCode;
            }

            @Override
            public String getRarity() {
                return rarity;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private CardRepository.CardTypeCountView typeCountView(String type, long count) {
        return new CardRepository.CardTypeCountView() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
