package com.pokade.domain.listing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

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

    private Listing saveListing(Long sellerId, Long cardId) {
        return listingRepository.save(
                Listing.builder()
                        .cardId(cardId)
                        .sellerId(sellerId)
                        .price(10000)
                        .build()
        );
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, terms_agreed_at) "
                                + "VALUES (:email, 'tester', 'LOCAL', 'USER', 'ACTIVE', now()) RETURNING id")
                .setParameter("email", email)
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Repo Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }
}
