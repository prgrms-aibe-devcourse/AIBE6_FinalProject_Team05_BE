package com.pokade.domain.watchlist.repository;

import com.pokade.domain.watchlist.entity.Watchlist;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WatchlistRepositoryTest {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findByUserId는_해당_유저가_등록한_항목을_모두_조회한다() {
        Long userId = insertUser("findbyuserid@test.com");
        Long cardA = insertCard("watch-find-a");
        Long cardB = insertCard("watch-find-b");
        saveWatchlist(userId, cardA, 1000, null);
        saveWatchlist(userId, cardB, null, 2000);

        List<Watchlist> found = watchlistRepository.findByUserId(userId);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Watchlist::getCardId).containsExactlyInAnyOrder(cardA, cardB);
    }

    @Test
    void existsByUserIdAndCardId는_등록된_조합이면_true_아니면_false() {
        Long userId = insertUser("exists@test.com");
        Long cardId = insertCard("watch-exists");
        Long otherCardId = insertCard("watch-exists-other");
        saveWatchlist(userId, cardId, 1000, null);

        assertThat(watchlistRepository.existsByUserIdAndCardId(userId, cardId)).isTrue();
        assertThat(watchlistRepository.existsByUserIdAndCardId(userId, otherCardId)).isFalse();
    }

    @Test
    void countByUserId는_등록_개수만큼_정확히_카운트된다() {
        Long userId = insertUser("count@test.com");
        Long otherUserId = insertUser("count-other@test.com");
        saveWatchlist(userId, insertCard("watch-count-a"), 1000, null);
        saveWatchlist(userId, insertCard("watch-count-b"), 1000, null);

        assertThat(watchlistRepository.countByUserId(userId)).isEqualTo(2);
        assertThat(watchlistRepository.countByUserId(otherUserId)).isEqualTo(0);
    }

    @Test
    void findByIdAndUserId는_본인_소유_항목만_조회된다() {
        Long ownerId = insertUser("owner@test.com");
        Long otherUserId = insertUser("other@test.com");
        Watchlist saved = saveWatchlist(ownerId, insertCard("watch-owned"), 1000, null);

        Optional<Watchlist> ownedResult = watchlistRepository.findByIdAndUserId(saved.getId(), ownerId);
        Optional<Watchlist> otherResult = watchlistRepository.findByIdAndUserId(saved.getId(), otherUserId);

        assertThat(ownedResult).isPresent();
        assertThat(otherResult).isEmpty();
    }

    @Test
    void findByIsNotifiedFalse는_알림_미발송_항목만_조회한다() {
        Long userId = insertUser("notified@test.com");
        Long notifiedCardId = insertCard("watch-notified-a");
        Long unnotifiedCardId = insertCard("watch-notified-b");
        Watchlist notified = saveWatchlist(userId, notifiedCardId, 1000, null);
        Watchlist unnotified = saveWatchlist(userId, unnotifiedCardId, 2000, null);
        notified.markAsNotified();
        watchlistRepository.save(notified);
        entityManager.flush();
        entityManager.clear();

        List<Watchlist> found = watchlistRepository.findByIsNotifiedFalse();

        // 전역 조회라 다른 테스트/기존 데이터의 미알림 워치리스트가 섞여 있을 수 있어, "정확히 이 목록만"이
        // 아니라 "이 테스트가 만든 미알림 항목은 포함되고, 알림 완료 항목은 제외되는지"만 검증한다.
        assertThat(found).extracting(Watchlist::getId)
                .contains(unnotified.getId())
                .doesNotContain(notified.getId());
    }

    @Test
    void findByListingNotifiedTrue는_재입고_알림을_보낸_항목만_조회한다() {
        Long userId = insertUser("listing-notified@test.com");
        Long notifiedCardId = insertCard("watch-listing-notified-a");
        Long unnotifiedCardId = insertCard("watch-listing-notified-b");
        Watchlist notified = saveWatchlist(userId, notifiedCardId, 1000, null);
        Watchlist unnotified = saveWatchlist(userId, unnotifiedCardId, 1000, null);
        notified.markAsListingNotified();
        watchlistRepository.save(notified);
        entityManager.flush();
        entityManager.clear();

        List<Watchlist> found = watchlistRepository.findByListingNotifiedTrue();

        // 전역 조회라 다른 테스트/기존 데이터가 섞여 있을 수 있어, "이 테스트가 만든 항목이 기대한 대로
        // 포함/제외되는지"만 검증한다(findByIsNotifiedFalse 테스트와 동일한 방식).
        assertThat(found).extracting(Watchlist::getId)
                .contains(notified.getId())
                .doesNotContain(unnotified.getId());
    }

    @Test
    void resetListingNotifiedIfTrue는_listingNotified가_true인_행만_false로_되돌리고_이미_false면_0을_반환한다() {
        Long userId = insertUser("reset-listing-notified@test.com");
        Watchlist notified = saveWatchlist(userId, insertCard("watch-reset-a"), 1000, null);
        Watchlist unnotified = saveWatchlist(userId, insertCard("watch-reset-b"), 1000, null);
        notified.markAsListingNotified();
        watchlistRepository.save(notified);
        entityManager.flush();

        int resetCount = watchlistRepository.resetListingNotifiedIfTrue(notified.getId());
        int noopCount = watchlistRepository.resetListingNotifiedIfTrue(unnotified.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(resetCount).isEqualTo(1);
        assertThat(noopCount).isEqualTo(0);
        assertThat(watchlistRepository.findById(notified.getId()).orElseThrow().isListingNotified()).isFalse();
    }

    @Test
    void countGroupedByCardIdIn은_카드별_등록수를_한번의_쿼리로_묶어서_반환한다() {
        Long userA = insertUser("count-grouped-a@test.com");
        Long userB = insertUser("count-grouped-b@test.com");
        Long userC = insertUser("count-grouped-c@test.com");
        Long cardWithTwo = insertCard("watch-grouped-two");
        Long cardWithOne = insertCard("watch-grouped-one");
        Long cardWithNone = insertCard("watch-grouped-none");
        saveWatchlist(userA, cardWithTwo, 1000, null);
        saveWatchlist(userB, cardWithTwo, 1000, null);
        saveWatchlist(userC, cardWithOne, 1000, null);

        List<WatchlistRepository.WatchlistCardCountView> counts =
                watchlistRepository.countGroupedByCardIdIn(List.of(cardWithTwo, cardWithOne, cardWithNone));

        // 등록이 없는 카드(cardWithNone)는 GROUP BY 결과에 행 자체가 생기지 않는다 - 0으로 채우는 건 서비스 계층 책임.
        assertThat(counts).extracting(WatchlistRepository.WatchlistCardCountView::getCardId,
                        WatchlistRepository.WatchlistCardCountView::getCount)
                .containsExactlyInAnyOrder(
                        tuple(cardWithTwo, 2L),
                        tuple(cardWithOne, 1L));
    }

    @Test
    void 같은_유저가_같은_카드로_두번_저장하면_UNIQUE_제약으로_예외가_발생한다() {
        Long userId = insertUser("dup@test.com");
        Long cardId = insertCard("watch-dup");
        saveWatchlist(userId, cardId, 1000, null);
        entityManager.flush();

        assertThatThrownBy(() -> {
            saveWatchlist(userId, cardId, 2000, null);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Watchlist saveWatchlist(Long userId, Long cardId, Integer targetBuyPrice, Integer targetSellPrice) {
        return watchlistRepository.save(
                Watchlist.builder()
                        .userId(userId)
                        .cardId(cardId)
                        .targetBuyPrice(targetBuyPrice)
                        .targetSellPrice(targetSellPrice)
                        .build()
        );
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE') RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue();
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Repo Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
    }
}
