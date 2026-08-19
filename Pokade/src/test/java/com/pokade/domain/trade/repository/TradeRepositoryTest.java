package com.pokade.domain.trade.repository;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.dto.MyTradeSearchCondition;
import com.pokade.domain.trade.dto.TradeRole;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.user.entity.User;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private Long sellerId() {
        return ((Number) entityManager.createNativeQuery(
                "SELECT id FROM users WHERE email = 'trade-seller@test.com'").getSingleResult()).longValue();
    }

    private Trade persistTrade(Listing listing, Long tradeBuyerId) {
        return tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(tradeBuyerId)
                        .price(listing.getPrice())
                        .build()
        );
    }

    // created_at은 @CreationTimestamp라 지정할 수 없어, 기간 경계 검증을 위해 직접 덮어씀
    private void overrideCreatedAt(Long tradeId, LocalDateTime createdAt) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE trades SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", tradeId)
                .executeUpdate();
        entityManager.clear();
    }

    private Page<Trade> findMine(Long userId, MyTradeSearchCondition condition, int size) {
        return tradeRepository.findMyTrades(
                userId,
                condition.includeBuy(),
                condition.includeSell(),
                condition.statusesOrAll(),
                condition.fromDateTime(),
                condition.toDateTimeExclusive(),
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")));
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

    @Test
    @DisplayName("t5 기간 내 체결 완료 건을 confirmed_at 오래된순으로 조회한다")
    void t5() {
        Long sellerId = ((Number) entityManager.createNativeQuery(
                "SELECT id FROM users WHERE email = 'trade-seller@test.com'").getSingleResult()).longValue();

        Listing psa10 = persistListing(sellerId, 5000000, ListingGrade.PSA10);
        Listing s = persistListing(sellerId, 3000000, ListingGrade.S);
        Listing raw = persistListing(sellerId, 2500000, null);

        persistCompletedTrade(psa10, LocalDateTime.now().minusDays(1));
        persistCompletedTrade(s, LocalDateTime.now().minusDays(3));
        persistCompletedTrade(raw, LocalDateTime.now().minusDays(10));

        List<Trade> result = tradeRepository.findCompletedTradesSince(
                cardId, TradeStatus.COMPLETED, LocalDateTime.now().minusDays(30));

        assertThat(result)
                .extracting(t -> t.getListing().getGrade())
                .containsExactly(null, ListingGrade.S, ListingGrade.PSA10);
    }

    @Test
    @DisplayName("t6 기준 시각 이전의 체결 완료 건은 조회되지 않는다")
    void t6() {
        Long sellerId = ((Number) entityManager.createNativeQuery(
                "SELECT id FROM users WHERE email = 'trade-seller@test.com'").getSingleResult()).longValue();

        Listing recent = persistListing(sellerId, 1000000, ListingGrade.A);
        Listing old = persistListing(sellerId, 2000000, ListingGrade.B);

        persistCompletedTrade(recent, LocalDateTime.now().minusDays(5));
        persistCompletedTrade(old, LocalDateTime.now().minusDays(100));

        List<Trade> result = tradeRepository.findCompletedTradesSince(
                cardId, TradeStatus.COMPLETED, LocalDateTime.now().minusDays(30));

        assertThat(result)
                .extracting(t -> t.getListing().getGrade())
                .containsExactly(ListingGrade.A);
    }

    @Test
    @DisplayName("t7 기간 내 체결 이력이 없으면 빈 목록을 반환한다")
    void t7() {
        List<Trade> result = tradeRepository.findCompletedTradesSince(
                cardId, TradeStatus.COMPLETED, LocalDateTime.now().minusDays(30));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t8 role 미지정이면 구매·판매 거래가 모두 조회된다")
    void t8() {
        Long otherId = sellerId();
        persistTrade(persistListing(otherId, 1000, ListingGrade.A), buyerId);   // 내가 구매자
        persistTrade(persistListing(buyerId, 2000, ListingGrade.B), otherId);   // 내가 판매자

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(null, null, null, null), 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("t9 role=BUY면 내가 구매자인 거래만 조회된다")
    void t9() {
        Long otherId = sellerId();
        persistTrade(persistListing(otherId, 1000, ListingGrade.A), buyerId);
        persistTrade(persistListing(buyerId, 2000, ListingGrade.B), otherId);

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(TradeRole.BUY, null, null, null), 20);

        assertThat(result.getContent())
                .singleElement()
                .satisfies(t -> assertThat(t.getBuyerId()).isEqualTo(buyerId));
    }

    @Test
    @DisplayName("t10 role=SELL이면 내가 판매자인 거래만 조회된다")
    void t10() {
        Long otherId = sellerId();
        persistTrade(persistListing(otherId, 1000, ListingGrade.A), buyerId);
        persistTrade(persistListing(buyerId, 2000, ListingGrade.B), otherId);

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(TradeRole.SELL, null, null, null), 20);

        assertThat(result.getContent())
                .singleElement()
                .satisfies(t -> assertThat(t.getListing().getSellerId()).isEqualTo(buyerId));
    }

    @Test
    @DisplayName("t11 status를 지정하면 해당 상태의 거래만 조회된다")
    void t11() {
        Long otherId = sellerId();
        persistTrade(persistListing(otherId, 1000, ListingGrade.A), buyerId);   // PENDING
        Trade cancelled = persistTrade(persistListing(otherId, 2000, ListingGrade.B), buyerId);
        cancelled.cancel();
        entityManager.flush();

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(null, List.of(TradeStatus.CANCELLED), null, null), 20);

        assertThat(result.getContent())
                .extracting(Trade::getStatus)
                .containsExactly(TradeStatus.CANCELLED);
    }

    @Test
    @DisplayName("t12 to에 지정한 날의 거래는 그날 늦은 시각이어도 포함되고, 다음 날은 제외된다")
    void t12() {
        Long otherId = sellerId();
        LocalDate targetDay = LocalDate.of(2026, 5, 10);

        Trade lastMinute = persistTrade(persistListing(otherId, 1000, ListingGrade.A), buyerId);
        overrideCreatedAt(lastMinute.getId(), targetDay.atTime(23, 30));

        Trade nextDay = persistTrade(persistListing(otherId, 2000, ListingGrade.B), buyerId);
        overrideCreatedAt(nextDay.getId(), targetDay.plusDays(1).atTime(0, 30));

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(null, null, targetDay, targetDay), 20);

        assertThat(result.getContent())
                .extracting(Trade::getId)
                .containsExactly(lastMinute.getId());
    }

    @Test
    @DisplayName("t13 내가 참여하지 않은 거래는 조회되지 않는다")
    void t13() {
        User outsider = User.createLocalUser("trade-outsider@test.com", "hashed", "outsider");
        entityManager.persist(outsider);
        entityManager.flush();

        persistTrade(persistListing(sellerId(), 1000, ListingGrade.A), outsider.getId());

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(null, null, null, null), 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("t14 페이지 크기를 넘겨도 totalElements는 전체 건수를 센다")
    void t14() {
        Long otherId = sellerId();
        persistTrade(persistListing(otherId, 1000, ListingGrade.A), buyerId);
        persistTrade(persistListing(otherId, 2000, ListingGrade.B), buyerId);
        persistTrade(persistListing(otherId, 3000, ListingGrade.S), buyerId);

        Page<Trade> result = findMine(buyerId,
                new MyTradeSearchCondition(null, null, null, null), 2);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }
}
