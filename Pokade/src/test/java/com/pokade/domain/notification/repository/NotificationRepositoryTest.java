package com.pokade.domain.notification.repository;

import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findByUserIdOrderByCreatedAtDesc는_같은_유저의_알림을_최신순으로_조회한다() {
        Long userId = insertUser("order@test.com");
        Notification first = saveNotification(userId, NotificationType.PRICE_TARGET, "first");
        Notification second = saveNotification(userId, NotificationType.TRADE_CONFIRMED, "second");
        Notification third = saveNotification(userId, NotificationType.LISTING_STALE, "third");

        List<Notification> found = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(found).extracting(Notification::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc는_다른_유저의_알림과_섞이지_않는다() {
        Long userId = insertUser("mine@test.com");
        Long otherUserId = insertUser("other@test.com");
        saveNotification(userId, NotificationType.PRICE_TARGET, "mine");
        saveNotification(otherUserId, NotificationType.PRICE_TARGET, "other");

        List<Notification> found = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void findByIdAndUserId는_본인_소유_알림만_조회된다() {
        Long ownerId = insertUser("owner@test.com");
        Long otherUserId = insertUser("other2@test.com");
        Notification saved = saveNotification(ownerId, NotificationType.PRICE_TARGET, "owned");

        Optional<Notification> ownedResult = notificationRepository.findByIdAndUserId(saved.getId(), ownerId);
        Optional<Notification> otherResult = notificationRepository.findByIdAndUserId(saved.getId(), otherUserId);

        assertThat(ownedResult).isPresent();
        assertThat(otherResult).isEmpty();
    }

    private Notification saveNotification(Long userId, NotificationType type, String message) {
        Notification saved = notificationRepository.save(
                Notification.builder()
                        .userId(userId)
                        .type(type)
                        .message(message)
                        .build()
        );
        entityManager.flush();
        return saved;
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status, terms_agreed_at) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE', now()) RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue();
    }
}
