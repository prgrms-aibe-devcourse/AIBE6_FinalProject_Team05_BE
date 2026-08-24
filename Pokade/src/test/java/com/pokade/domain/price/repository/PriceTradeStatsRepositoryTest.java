package com.pokade.domain.price.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.domain.user.entity.User;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class PriceTradeStatsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PriceTradeStatsRepository priceTradeStatsRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private EntityManager entityManager;

    private Long cardId;
    private Long sellerId;
    private Long buyerId;

    @BeforeEach
    void setUp() {
        Card card = Card.builder().name("Charizard").build();
        entityManager.persist(card);
        cardId = card.getId();

        User seller = User.createLocalUser("stats-seller@test.com", "hashed", "seller");
        User buyer = User.createLocalUser("stats-buyer@test.com", "hashed", "buyer");
        entityManager.persist(seller);
        entityManager.persist(buyer);
        sellerId = seller.getId();
        buyerId = buyer.getId();
    }

    private Listing persistListing(int price, ListingGrade grade) {
        return listingRepository.save(
                Listing.builder().cardId(cardId).sellerId(sellerId).price(price).grade(grade).build());
    }

    private Trade persistCompletedTrade(Listing listing, LocalDateTime confirmedAt) {
        Trade trade = tradeRepository.save(
                Trade.builder().listing(listing).buyerId(buyerId).price(listing.getPrice()).build());
        trade.shipToPlatform();
        trade.markInspected();
        trade.markDelivered();
        trade.complete();
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE trades SET confirmed_at = :confirmedAt WHERE id = :id")
                .setParameter("confirmedAt", confirmedAt)
                .setParameter("id", trade.getId())
                .executeUpdate();
        entityManager.clear();
        return trade;
    }

    @Test
    @DisplayName("t1 최근 블록(since) 내 등급별 체결 평균가를 계산한다")
    void t1() {
        Listing a = persistListing(3000000, ListingGrade.S);
        Listing b = persistListing(3100000, ListingGrade.S);
        Listing outsideWindow = persistListing(2830000, ListingGrade.S);
        Listing otherGrade = persistListing(5000000, ListingGrade.PSA10);

        persistCompletedTrade(a, LocalDateTime.now().minusDays(5));
        persistCompletedTrade(b, LocalDateTime.now().minusDays(1));
        persistCompletedTrade(outsideWindow, LocalDateTime.now().minusDays(15));
        persistCompletedTrade(otherGrade, LocalDateTime.now().minusDays(1));

        Double avg = priceTradeStatsRepository.findAveragePriceByGradeSince(
                cardId, ListingGrade.S, TradeStatus.COMPLETED, LocalDateTime.now().minusDays(7));

        assertThat(avg).isEqualTo(3050000.0);
    }

    @Test
    @DisplayName("t2 이전 블록([from, to)) 내 등급별 체결 평균가를 계산한다")
    void t2() {
        Listing withinBlock = persistListing(2830000, ListingGrade.S);
        Listing beforeBlock = persistListing(2630000, ListingGrade.S);
        Listing afterBlock = persistListing(3000000, ListingGrade.S);

        persistCompletedTrade(withinBlock, LocalDateTime.now().minusDays(10));
        persistCompletedTrade(beforeBlock, LocalDateTime.now().minusDays(20));
        persistCompletedTrade(afterBlock, LocalDateTime.now().minusDays(3));

        Double avg = priceTradeStatsRepository.findAveragePriceByGradeBetween(
                cardId, ListingGrade.S, TradeStatus.COMPLETED,
                LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(7));

        assertThat(avg).isEqualTo(2830000.0);
    }

    @Test
    @DisplayName("t3 해당 블록에 등급별 체결 이력이 없으면 null을 반환한다")
    void t3() {
        Listing recent = persistListing(3000000, ListingGrade.S);
        persistCompletedTrade(recent, LocalDateTime.now().minusDays(3));

        Double avg = priceTradeStatsRepository.findAveragePriceByGradeBetween(
                cardId, ListingGrade.S, TradeStatus.COMPLETED,
                LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(7));

        assertThat(avg).isNull();
    }

    @Test
    @DisplayName("t4 기간 내 등급별 체결 건수를 집계한다")
    void t4() {
        Listing withinWindow = persistListing(3000000, ListingGrade.S);
        Listing outsideWindow = persistListing(2830000, ListingGrade.S);
        Listing otherGrade = persistListing(5000000, ListingGrade.PSA10);

        persistCompletedTrade(withinWindow, LocalDateTime.now().minusDays(3));
        persistCompletedTrade(outsideWindow, LocalDateTime.now().minusDays(15));
        persistCompletedTrade(otherGrade, LocalDateTime.now().minusDays(1));

        long count = priceTradeStatsRepository.countCompletedTradesByGradeSince(
                cardId, ListingGrade.S, TradeStatus.COMPLETED, LocalDateTime.now().minusDays(7));

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("t5 워치리스트 등록(createdAt) 이후 체결분만 반영해 카드별 최저/최고가를 계산한다")
    void t5() {
        Listing beforeRegistration = persistListing(2000000, ListingGrade.S);
        Listing afterRegistrationLow = persistListing(3000000, ListingGrade.S);
        Listing afterRegistrationHigh = persistListing(3500000, ListingGrade.S);

        LocalDateTime registeredAt = LocalDateTime.now().minusDays(5);

        persistCompletedTrade(beforeRegistration, registeredAt.minusDays(1));
        persistCompletedTrade(afterRegistrationLow, registeredAt.plusDays(1));
        persistCompletedTrade(afterRegistrationHigh, registeredAt.plusDays(2));

        var ranges = priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                List.of(cardId), null, TradeStatus.COMPLETED, registeredAt);

        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).getCardId()).isEqualTo(cardId);
        assertThat(ranges.get(0).getMinPrice()).isEqualTo(3000000);
        assertThat(ranges.get(0).getMaxPrice()).isEqualTo(3500000);
    }

    @Test
    @DisplayName("t6 등록 이후 체결 이력이 없으면 결과에서 제외된다")
    void t6() {
        Listing beforeRegistration = persistListing(2000000, ListingGrade.S);

        LocalDateTime registeredAt = LocalDateTime.now().minusDays(5);
        persistCompletedTrade(beforeRegistration, registeredAt.minusDays(1));

        var ranges = priceTradeStatsRepository.findPriceRangesByCardIdsSince(
                List.of(cardId), null, TradeStatus.COMPLETED, registeredAt);

        assertThat(ranges).isEmpty();
    }

    @Test
    @DisplayName("t7 IN (:cardIds)절에 카드 여러 개를 넘겨도 각 카드별로 정확히 매칭돼 확장된다")
    void t7() {
        Card secondCard = Card.builder().name("Blastoise").build();
        entityManager.persist(secondCard);
        Long secondCardId = secondCard.getId();

        Listing firstCardListing = persistListing(3000000, ListingGrade.S);
        Listing secondCardListing = listingRepository.save(
                Listing.builder().cardId(secondCardId).sellerId(sellerId).price(5000000).grade(ListingGrade.S).build());

        persistCompletedTrade(firstCardListing, LocalDateTime.now().minusDays(1));
        persistCompletedTrade(secondCardListing, LocalDateTime.now().minusDays(1));

        var ranges = priceTradeStatsRepository.findPriceRangesByCardIds(
                List.of(cardId, secondCardId), null, TradeStatus.COMPLETED);

        assertThat(ranges).hasSize(2);
        assertThat(ranges).extracting(PriceTradeStatsRepository.CardPriceRangeView::getCardId)
                .containsExactlyInAnyOrder(cardId, secondCardId);
        assertThat(ranges).filteredOn(r -> r.getCardId().equals(cardId))
                .extracting(PriceTradeStatsRepository.CardPriceRangeView::getMinPrice)
                .containsExactly(3000000);
        assertThat(ranges).filteredOn(r -> r.getCardId().equals(secondCardId))
                .extracting(PriceTradeStatsRepository.CardPriceRangeView::getMinPrice)
                .containsExactly(5000000);
    }

    @Test
    @DisplayName("t8 findPriceRangesByCardIdsSincePerCard()는 카드별로 서로 다른 등록 시점(since)을 정확히 페어링해서 적용한다(#275)")
    void t8() {
        Card secondCard = Card.builder().name("Blastoise").build();
        entityManager.persist(secondCard);
        Long secondCardId = secondCard.getId();

        // 카드1: registeredAt1 이전/이후 체결 섞임 - registeredAt1 이후만 잡혀야 함(2000000 제외, 3000000만)
        LocalDateTime registeredAt1 = LocalDateTime.now().minusDays(5);
        Listing card1Before = persistListing(2000000, ListingGrade.S);
        Listing card1After = persistListing(3000000, ListingGrade.S);
        persistCompletedTrade(card1Before, registeredAt1.minusDays(1));
        persistCompletedTrade(card1After, registeredAt1.plusDays(1));

        // 카드2: registeredAt2(카드1과 다른 시점) 기준 - registeredAt2 이전 체결(4000000)은 제외, 이후(5000000)만 포함
        LocalDateTime registeredAt2 = LocalDateTime.now().minusDays(20);
        Listing card2Before = Listing.builder().cardId(secondCardId).sellerId(sellerId).price(4000000).grade(ListingGrade.S).build();
        Listing card2After = Listing.builder().cardId(secondCardId).sellerId(sellerId).price(5000000).grade(ListingGrade.S).build();
        listingRepository.save(card2Before);
        listingRepository.save(card2After);
        persistCompletedTrade(card2Before, registeredAt2.minusDays(1));
        persistCompletedTrade(card2After, registeredAt2.plusDays(1));

        var ranges = priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(
                List.of(cardId, secondCardId),
                List.of(registeredAt1, registeredAt2),
                null,
                TradeStatus.COMPLETED);

        assertThat(ranges).hasSize(2);
        assertThat(ranges).filteredOn(r -> r.getCardId().equals(cardId))
                .extracting(PriceTradeStatsRepository.CardPriceRangeView::getMinPrice,
                        PriceTradeStatsRepository.CardPriceRangeView::getMaxPrice)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3000000, 3000000));
        assertThat(ranges).filteredOn(r -> r.getCardId().equals(secondCardId))
                .extracting(PriceTradeStatsRepository.CardPriceRangeView::getMinPrice,
                        PriceTradeStatsRepository.CardPriceRangeView::getMaxPrice)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(5000000, 5000000));
    }

    @Test
    @DisplayName("t9 findPriceRangesByCardIdsSincePerCard()는 grade를 지정하면 해당 등급 체결만 반영한다(#275)")
    void t9() {
        LocalDateTime registeredAt = LocalDateTime.now().minusDays(5);
        Listing sGrade = persistListing(3000000, ListingGrade.S);
        Listing otherGrade = persistListing(9000000, ListingGrade.PSA10);
        persistCompletedTrade(sGrade, registeredAt.plusDays(1));
        persistCompletedTrade(otherGrade, registeredAt.plusDays(1));

        var ranges = priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(
                List.of(cardId),
                List.of(registeredAt),
                ListingGrade.S,
                TradeStatus.COMPLETED);

        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).getMinPrice()).isEqualTo(3000000);
        assertThat(ranges.get(0).getMaxPrice()).isEqualTo(3000000);
    }

    @Test
    @DisplayName("t10 findDailyMarketStats()는 카드/등급 구분 없이 일 단위로 거래량과 거래가 평균을 계산한다")
    void t10() {
        Listing a = persistListing(1000000, ListingGrade.S);
        Listing b = persistListing(2000000, ListingGrade.PSA10);
        Listing c = persistListing(3000000, ListingGrade.A);
        Listing outsideWindow = persistListing(9000000, ListingGrade.S);

        java.time.LocalDate today = java.time.LocalDate.now();
        // 같은 날(오늘)에 3건 체결 - 평균은 (1000000+2000000+3000000)/3 = 2000000이어야 한다.
        persistCompletedTrade(a, today.atTime(9, 0));
        persistCompletedTrade(b, today.atTime(15, 0));
        persistCompletedTrade(c, today.atTime(21, 0));
        // 조회 범위(from) 밖의 체결은 집계에서 제외돼야 한다.
        persistCompletedTrade(outsideWindow, today.minusDays(40).atTime(12, 0));

        List<PriceTradeStatsRepository.DailyMarketStatView> stats = priceTradeStatsRepository
                .findDailyMarketStats(TradeStatus.COMPLETED, today.minusDays(29).atStartOfDay());

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getTradeDate()).isEqualTo(today);
        assertThat(stats.get(0).getVolume()).isEqualTo(3L);
        assertThat(stats.get(0).getAvgPrice()).isEqualTo(2000000.0);
    }
}
