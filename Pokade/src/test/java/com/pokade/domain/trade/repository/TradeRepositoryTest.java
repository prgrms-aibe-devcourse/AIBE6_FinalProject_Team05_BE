package com.pokade.domain.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.user.entity.User;
import com.pokade.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
class TradeRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private EntityManager entityManager;

    private Long cardId;
    private Long buyerId;

    @BeforeEach
    void setUp() {
        Card card = Card.builder().name("Charizard").build();
        entityManager.persist(card);
        cardId = card.getId();

        User seller = User.createLocalUser("trade-seller@test.com", "hashed", "seller");
        User buyer = User.createLocalUser("trade-buyer@test.com", "hashed", "buyer");
        entityManager.persist(seller);
        entityManager.persist(buyer);
        buyerId = buyer.getId();
    }

    private Listing persistListing(Long sellerId, int price, ListingGrade grade) {
        return listingRepository.save(
                Listing.builder()
                        .cardId(cardId)
                        .sellerId(sellerId)
                        .price(price)
                        .grade(grade)
                        .build()
        );
    }

    private Trade persistCompletedTrade(Listing listing, LocalDateTime confirmedAt) {
        Trade trade = tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(buyerId)
                        .price(listing.getPrice())
                        .build()
        );
        trade.complete();
        entityManager.flush();
        // complete()는 confirmed_at을 항상 now()로 고정하므로, 정렬 검증을 위해 원하는 시각으로 직접 덮어씀
        entityManager.createNativeQuery("UPDATE trades SET confirmed_at = :confirmedAt WHERE id = :id")
                .setParameter("confirmedAt", confirmedAt)
                .setParameter("id", trade.getId())
                .executeUpdate();
        entityManager.clear();
        return trade;
    }

    @Test
    @DisplayName("t1 체결 완료 건이 여러 개면 confirmed_at 최신순으로 조회된다")
    void t1() {
        Long sellerId = ((Number) entityManager.createNativeQuery(
                "SELECT id FROM users WHERE email = 'trade-seller@test.com'").getSingleResult()).longValue();

        Listing psa10 = persistListing(sellerId, 5000000, ListingGrade.PSA10);
        Listing s = persistListing(sellerId, 3000000, ListingGrade.S);
        Listing raw = persistListing(sellerId, 2500000, null);

        persistCompletedTrade(psa10, LocalDateTime.now().minusDays(1));
        persistCompletedTrade(s, LocalDateTime.now().minusDays(3));
        persistCompletedTrade(raw, LocalDateTime.now().minusDays(10));

        List<Trade> result = tradeRepository.findRecentCompletedTrades(
                cardId, TradeStatus.COMPLETED, PageRequest.of(0, 20));

        assertThat(result)
                .extracting(t -> t.getListing().getGrade())
                .containsExactly(ListingGrade.PSA10, ListingGrade.S, null);
    }

    @Test
    @DisplayName("t2 COMPLETED가 아닌 거래는 조회되지 않는다")
    void t2() {
        Long sellerId = ((Number) entityManager.createNativeQuery(
                "SELECT id FROM users WHERE email = 'trade-seller@test.com'").getSingleResult()).longValue();

        Listing listing = persistListing(sellerId, 1000000, ListingGrade.A);
        tradeRepository.save(
                Trade.builder().listing(listing).buyerId(buyerId).price(1000000).build()
        );

        List<Trade> result = tradeRepository.findRecentCompletedTrades(
                cardId, TradeStatus.COMPLETED, PageRequest.of(0, 20));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t3 Pageable로 상위 N건만 조회된다")
    void t3() {
        Long sellerId = ((Number) entityManager.createNativeQuery(
                "SELECT id FROM users WHERE email = 'trade-seller@test.com'").getSingleResult()).longValue();

        Listing first = persistListing(sellerId, 1000000, ListingGrade.A);
        Listing second = persistListing(sellerId, 2000000, ListingGrade.B);
        Listing third = persistListing(sellerId, 3000000, ListingGrade.S);

        persistCompletedTrade(first, LocalDateTime.now().minusDays(1));
        persistCompletedTrade(second, LocalDateTime.now().minusDays(2));
        persistCompletedTrade(third, LocalDateTime.now().minusDays(3));

        List<Trade> result = tradeRepository.findRecentCompletedTrades(
                cardId, TradeStatus.COMPLETED, PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("t4 체결 이력이 없는 카드는 빈 목록을 반환한다")
    void t4() {
        Card otherCard = Card.builder().name("Pikachu").build();
        entityManager.persist(otherCard);

        List<Trade> result = tradeRepository.findRecentCompletedTrades(
                otherCard.getId(), TradeStatus.COMPLETED, PageRequest.of(0, 20));

        assertThat(result).isEmpty();
    }
}
