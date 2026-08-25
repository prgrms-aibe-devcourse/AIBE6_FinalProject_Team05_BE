package com.pokade.domain.listing.repository;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ListingRepositoryTest {

    @Autowired
    private ListingRepository listingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void ACTIVE_매물은_markAsTrading_호출시_TRADING으로_전환되고_1을_반환한다() {
        Listing listing = saveListing(insertUser("seller-a@test.com"), insertCard("mark-trading-1"));

        int updated = listingRepository.markAsTrading(listing.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        Listing found = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ListingStatus.TRADING);
    }

    @Test
    void 이미_ACTIVE가_아닌_매물은_markAsTrading_호출시_0을_반환하고_상태가_바뀌지_않는다() {
        Listing listing = saveListing(insertUser("seller-b@test.com"), insertCard("mark-trading-2"));
        listingRepository.markAsTrading(listing.getId());
        entityManager.clear();

        int secondAttempt = listingRepository.markAsTrading(listing.getId());
        entityManager.clear();

        assertThat(secondAttempt).isEqualTo(0);
        Listing found = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ListingStatus.TRADING);
    }

    @Test
    void 존재하지_않는_매물은_markAsTrading_호출시_0을_반환한다() {
        int updated = listingRepository.markAsTrading(999_999_999L);

        assertThat(updated).isEqualTo(0);
    }

    @Test
    void HIDDEN이_아닌_매물은_hideIfNotAlreadyHidden_호출시_HIDDEN으로_전환되고_1을_반환한다() {
        Listing listing = saveListing(insertUser("seller-c@test.com"), insertCard("hide-1"));

        int updated = listingRepository.hideIfNotAlreadyHidden(listing.getId());
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        Listing found = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ListingStatus.HIDDEN);
    }

    @Test
    void 이미_HIDDEN인_매물은_hideIfNotAlreadyHidden_호출시_0을_반환한다() {
        Listing listing = saveListing(insertUser("seller-d@test.com"), insertCard("hide-2"));
        listingRepository.hideIfNotAlreadyHidden(listing.getId());
        entityManager.clear();

        // 동시에 두 요청이 들어와도 하나만 성공해야 함을, 순차 재호출로 검증한다
        // (실제 동시성 안전성은 DB 행 잠금이 보장하고, 여기서는 조건부 UPDATE의 결과 값 계약을 검증)
        int secondAttempt = listingRepository.hideIfNotAlreadyHidden(listing.getId());
        entityManager.clear();

        assertThat(secondAttempt).isEqualTo(0);
        Listing found = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ListingStatus.HIDDEN);
    }

    @Test
    void 존재하지_않는_매물은_hideIfNotAlreadyHidden_호출시_0을_반환한다() {
        int updated = listingRepository.hideIfNotAlreadyHidden(999_999_999L);

        assertThat(updated).isEqualTo(0);
    }

    // findOrderbook은 호가창(GET /api/listings/{cardId}/orderbook)과 구매입찰 등록 알림
    // (BuyOfferReceivedNoticeListener)이 함께 쓰는 쿼리다. 두 호출부 모두 "정지/탈퇴한 판매자는
    // 빠진다"를 전제로 동작하는데(알림 쪽은 그 전제를 근거로 수신자 필터를 따로 두지 않는다),
    // 그 전제를 만드는 것은 JPQL 안의 서브쿼리 하나뿐이라 여기서 직접 고정한다.
    @Test
    void findOrderbook은_ACTIVE가_아닌_판매자의_매물을_제외한다() {
        Long cardId = insertCard("orderbook-seller-filter");
        Long variantId = insertVariant(cardId);
        Long activeSeller = insertUser("orderbook-active@test.com", "ACTIVE");
        Listing visible = saveListing(activeSeller, cardId, variantId);
        // 알림이 절대 가면 안 되는 상태들 - SUSPENDED/DELETED뿐 아니라 아직 ACTIVE가 된 적 없는
        // PENDING, 탈퇴 진행 중인 WITHDRAWAL_PENDING까지 같은 서브쿼리 하나로 걸러진다.
        for (String status : List.of("SUSPENDED", "DELETED", "PENDING", "WITHDRAWAL_PENDING")) {
            saveListing(insertUser("orderbook-" + status + "@test.com", status), cardId, variantId);
        }
        entityManager.flush();
        entityManager.clear();

        List<Listing> orderbook = listingRepository.findOrderbook(
                cardId, variantId, ListingStatus.ACTIVE, null);

        assertThat(orderbook)
                .extracting(Listing::getId)
                .containsExactly(visible.getId());
    }

    // 판매자가 ACTIVE라도 매물 자체가 ACTIVE가 아니면 빠진다 - 위 테스트가 판매자 조건만 보므로
    // 매물 상태 조건이 함께 살아있는지도 같이 고정해 둔다.
    @Test
    void findOrderbook은_ACTIVE가_아닌_매물을_제외한다() {
        Long cardId = insertCard("orderbook-status-filter");
        Long variantId = insertVariant(cardId);
        Long sellerId = insertUser("orderbook-status@test.com", "ACTIVE");
        Listing active = saveListing(sellerId, cardId, variantId);
        Listing hidden = saveListing(sellerId, cardId, variantId);
        listingRepository.hideIfNotAlreadyHidden(hidden.getId());
        entityManager.flush();
        entityManager.clear();

        List<Listing> orderbook = listingRepository.findOrderbook(
                cardId, variantId, ListingStatus.ACTIVE, null);

        assertThat(orderbook)
                .extracting(Listing::getId)
                .containsExactly(active.getId());
    }

    private Listing saveListing(Long sellerId, Long cardId) {
        return saveListing(sellerId, cardId, null);
    }

    private Listing saveListing(Long sellerId, Long cardId, Long variantId) {
        return listingRepository.save(
                Listing.builder()
                        .cardId(cardId)
                        .sellerId(sellerId)
                        .variantId(variantId)
                        .price(10000)
                        .build()
        );
    }

    private Long insertUser(String email) {
        return insertUser(email, "ACTIVE");
    }

    // nickname은 UNIQUE라 한 테스트에서 유저를 여럿 만들 때 고정값을 쓰면 제약에 걸린다 - email에서 파생시킨다.
    private Long insertUser(String email, String status) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', :status) RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .setParameter("status", status)
                .getSingleResult()).longValue();
    }

    private Long insertVariant(Long cardId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO card_variants (card_id, variant_name, is_primary, synced_at) "
                                + "VALUES (:cardId, 'repo-test-variant', true, now()) RETURNING id")
                .setParameter("cardId", cardId)
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Repo Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }
}
