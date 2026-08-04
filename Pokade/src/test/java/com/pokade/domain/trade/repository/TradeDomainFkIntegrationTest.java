package com.pokade.domain.trade.repository;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Payment;
import com.pokade.domain.trade.entity.PaymentMethod;
import com.pokade.domain.trade.entity.Trade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TradeDomainFkIntegrationTest {

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 매물_거래_결제가_실제_users_cards_행을_참조해서_저장된다() {
        Long sellerId = insertUser("seller-fk-test@pokade.com");
        Long buyerId = insertUser("buyer-fk-test@pokade.com");
        Long cardId = insertCard("fk-test-card-1");
        Long variantId = insertCardVariant(cardId, "default");

        Listing listing = listingRepository.save(
                Listing.builder()
                        .cardId(cardId)
                        .sellerId(sellerId)
                        .variantId(variantId)
                        .price(10000)
                        .grade(ListingGrade.A)
                        .build()
        );

        Trade trade = tradeRepository.save(
                Trade.builder()
                        .listing(listing)
                        .buyerId(buyerId)
                        .price(10000)
                        .build()
        );

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .trade(trade)
                        .buyerId(buyerId)
                        .amount(10000)
                        .method(PaymentMethod.CARD)
                        .build()
        );

        entityManager.flush();
        entityManager.clear();

        Payment found = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(found.getTrade().getId()).isEqualTo(trade.getId());
        assertThat(found.getTrade().getListing().getId()).isEqualTo(listing.getId());
        assertThat(found.getTrade().getListing().getSellerId()).isEqualTo(sellerId);
    }

    @Test
    void 존재하지_않는_seller_id로_매물을_저장하면_FK_제약으로_실패한다() {
        Listing invalid = Listing.builder()
                .cardId(insertCard("fk-test-card-invalid"))
                .sellerId(999_999_999L)
                .price(1000)
                .build();

        assertThatThrownBy(() -> listingRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertUser(String email) {
        String nickname = email.split("@")[0];
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, terms_agreed_at) " +
                                "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE', now()) RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", nickname)
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'FK Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }

    private Long insertCardVariant(Long cardId, String variantName) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO card_variants (card_id, variant_name, synced_at) VALUES (:cardId, :variantName, now()) RETURNING id")
                .setParameter("cardId", cardId)
                .setParameter("variantName", variantName)
                .getSingleResult()).longValue();
    }
}
