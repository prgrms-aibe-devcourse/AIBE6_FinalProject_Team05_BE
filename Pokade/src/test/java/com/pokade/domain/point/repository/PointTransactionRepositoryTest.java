package com.pokade.domain.point.repository;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.point.entity.PointTransaction;
import com.pokade.domain.point.entity.PointTransactionType;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.user.entity.User;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PointTransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = User.createLocalUser("point-user@test.com", "hashed", "tester");
        entityManager.persist(user);
        userId = user.getId();
    }

    @Test
    @DisplayName("충전 이력을 저장하면 타입/금액/충전 후 잔액이 그대로 조회된다")
    void save_charge_persistsFields() {
        PointTransaction transaction = pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(userId)
                        .type(PointTransactionType.CHARGE)
                        .amount(10000)
                        .balanceAfter(10000)
                        .build()
        );
        entityManager.clear();

        PointTransaction found = pointTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getType()).isEqualTo(PointTransactionType.CHARGE);
        assertThat(found.getAmount()).isEqualTo(10000);
        assertThat(found.getBalanceAfter()).isEqualTo(10000);
        assertThat(found.getRelatedTradeId()).isNull();
        assertThat(found.getRelatedGradeResultId()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("매물 구매로 인한 차감 이력은 related_trade_id를 함께 저장한다")
    void save_use_withRelatedTradeId() {
        Card card = Card.builder().name("Charizard").build();
        entityManager.persist(card);
        User seller = User.createLocalUser("point-seller@test.com", "hashed", "seller");
        entityManager.persist(seller);
        Listing listing = Listing.builder().cardId(card.getId()).sellerId(seller.getId()).price(5000).build();
        entityManager.persist(listing);
        Trade trade = Trade.builder().listing(listing).buyerId(userId).price(5000).build();
        entityManager.persist(trade);

        PointTransaction transaction = pointTransactionRepository.save(
                PointTransaction.builder()
                        .userId(userId)
                        .type(PointTransactionType.USE)
                        .amount(5000)
                        .balanceAfter(5000)
                        .relatedTradeId(trade.getId())
                        .build()
        );
        entityManager.clear();

        PointTransaction found = pointTransactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo(PointTransactionType.USE);
        assertThat(found.getRelatedTradeId()).isEqualTo(trade.getId());
    }

    @Test
    @DisplayName("존재하지 않는 유저 id로 저장하면 FK 제약 위반으로 실패한다")
    void save_unknownUserId_violatesForeignKey() {
        PointTransaction transaction = PointTransaction.builder()
                .userId(999_999_999L)
                .type(PointTransactionType.CHARGE)
                .amount(1000)
                .balanceAfter(1000)
                .build();

        assertThatThrownBy(() -> {
            pointTransactionRepository.save(transaction);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
