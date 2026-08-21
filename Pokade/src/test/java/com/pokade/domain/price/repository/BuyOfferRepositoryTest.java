package com.pokade.domain.price.repository;

import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.price.entity.BuyOffer;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BuyOfferRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private BuyOfferRepository buyOfferRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("t1 grade 필터 없이 조회하면 ACTIVE 구매입찰을 가격 내림차순으로 반환한다")
    void t1() {
        Long buyerId = insertUser("buyer-a@test.com");
        Long cardId = insertCard("buy-offer-orderbook-1");
        Long variantId = insertVariant(cardId, "unlimitedHolofoil");

        insertBuyOffer(cardId, buyerId, variantId, 2700000, null, "ACTIVE");
        insertBuyOffer(cardId, buyerId, variantId, 3100000, "S", "ACTIVE");
        insertBuyOffer(cardId, buyerId, variantId, 2900000, "PSA10", "ACTIVE");
        insertBuyOffer(cardId, buyerId, variantId, 9999000, "S", "EXPIRED");

        List<BuyOffer> result = buyOfferRepository.findOrderbook(cardId, variantId, null);

        assertThat(result).extracting(BuyOffer::getPrice).containsExactly(3100000, 2900000, 2700000);
    }

    @Test
    @DisplayName("t2 grade를 지정하면 해당 등급만 가격 내림차순으로 반환한다")
    void t2() {
        Long buyerId = insertUser("buyer-b@test.com");
        Long cardId = insertCard("buy-offer-orderbook-2");
        Long variantId = insertVariant(cardId, "unlimitedHolofoil");

        insertBuyOffer(cardId, buyerId, variantId, 2700000, "S", "ACTIVE");
        insertBuyOffer(cardId, buyerId, variantId, 3100000, "S", "ACTIVE");
        insertBuyOffer(cardId, buyerId, variantId, 5000000, "PSA10", "ACTIVE");

        List<BuyOffer> result = buyOfferRepository.findOrderbook(cardId, variantId, ListingGrade.S);

        assertThat(result).extracting(BuyOffer::getPrice).containsExactly(3100000, 2700000);
        assertThat(result).allMatch(offer -> offer.getGrade() == ListingGrade.S);
    }

    @Test
    @DisplayName("t3 활성 구매입찰이 없으면 빈 목록을 반환한다")
    void t3() {
        Long cardId = insertCard("buy-offer-orderbook-3");
        Long variantId = insertVariant(cardId, "unlimitedHolofoil");

        List<BuyOffer> result = buyOfferRepository.findOrderbook(cardId, variantId, null);

        assertThat(result).isEmpty();
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, 'tester', 'LOCAL', 'USER', 'ACTIVE') RETURNING id")
                .setParameter("email", email)
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Repo Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }

    private Long insertVariant(Long cardId, String variantName) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO card_variants (card_id, variant_name, synced_at) "
                                + "VALUES (:cardId, :variantName, now()) RETURNING id")
                .setParameter("cardId", cardId)
                .setParameter("variantName", variantName)
                .getSingleResult()).longValue();
    }

    private void insertBuyOffer(Long cardId, Long buyerId, Long variantId, int price, String grade, String status) {
        entityManager.createNativeQuery(
                        "INSERT INTO buy_offers (card_id, buyer_id, variant_id, price, grade, status) "
                                + "VALUES (:cardId, :buyerId, :variantId, :price, :grade, :status)")
                .setParameter("cardId", cardId)
                .setParameter("buyerId", buyerId)
                .setParameter("variantId", variantId)
                .setParameter("price", price)
                .setParameter("grade", grade)
                .setParameter("status", status)
                .executeUpdate();
    }
}
