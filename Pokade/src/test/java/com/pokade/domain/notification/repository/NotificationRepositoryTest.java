package com.pokade.domain.notification.repository;

import com.pokade.domain.notification.entity.Notification;
import com.pokade.domain.notification.entity.NotificationType;
import com.pokade.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findAllByUserIdForTestVerification는_같은_유저의_알림을_최신순으로_조회한다() {
        Long userId = insertUser("order@test.com");
        Notification first = saveNotification(userId, NotificationType.PRICE_TARGET, "first");
        Notification second = saveNotification(userId, NotificationType.TRADE_CONFIRMED, "second");
        Notification third = saveNotification(userId, NotificationType.LISTING_STALE, "third");

        List<Notification> found = notificationRepository.findAllByUserIdForTestVerification(userId);

        assertThat(found).extracting(Notification::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void findAllByUserIdForTestVerification는_다른_유저의_알림과_섞이지_않는다() {
        Long userId = insertUser("mine@test.com");
        Long otherUserId = insertUser("other@test.com");
        saveNotification(userId, NotificationType.PRICE_TARGET, "mine");
        saveNotification(otherUserId, NotificationType.PRICE_TARGET, "other");

        List<Notification> found = notificationRepository.findAllByUserIdForTestVerification(userId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void findByUserId_페이징은_같은_유저의_알림을_요청한_정렬로_페이지_단위로_조회한다() {
        Long userId = insertUser("paging@test.com");
        Notification first = saveNotification(userId, NotificationType.PRICE_TARGET, "first");
        Notification second = saveNotification(userId, NotificationType.TRADE_CONFIRMED, "second");
        Notification third = saveNotification(userId, NotificationType.LISTING_STALE, "third");

        Page<Notification> firstPage = notificationRepository.findByUserId(
                userId, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(firstPage.getContent()).extracting(Notification::getId)
                .containsExactly(third.getId(), second.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);

        Page<Notification> secondPage = notificationRepository.findByUserId(
                userId, PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(secondPage.getContent()).extracting(Notification::getId)
                .containsExactly(first.getId());
    }

    @Test
    void findByUserId_페이징은_다른_유저의_알림과_섞이지_않는다() {
        Long userId = insertUser("paging-mine@test.com");
        Long otherUserId = insertUser("paging-other@test.com");
        saveNotification(userId, NotificationType.PRICE_TARGET, "mine");
        saveNotification(otherUserId, NotificationType.PRICE_TARGET, "other");

        Page<Notification> found = notificationRepository.findByUserId(userId, PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void findByUserId_페이징은_알림이_없으면_빈_페이지를_반환한다() {
        Long userId = insertUser("paging-empty@test.com");

        Page<Notification> found = notificationRepository.findByUserId(userId, PageRequest.of(0, 20));

        assertThat(found.getContent()).isEmpty();
        assertThat(found.getTotalElements()).isZero();
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

    @Test
    void markAsReadIfUnread는_읽지_않은_알림을_1건_갱신하고_이후_재호출은_0건이다() {
        Long userId = insertUser("mark-unread@test.com");
        Notification saved = saveNotification(userId, NotificationType.PRICE_TARGET, "unread");

        int firstResult = notificationRepository.markAsReadIfUnread(saved.getId(), userId);
        entityManager.flush();
        entityManager.clear();
        // 두 번째 호출이 "동시에 들어온 다른 요청"을 대신한다 - is_read=false 조건이 이미 거짓이라 0건이어야 한다.
        int secondResult = notificationRepository.markAsReadIfUnread(saved.getId(), userId);

        assertThat(firstResult).isEqualTo(1);
        assertThat(secondResult).isEqualTo(0);
        Notification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isTrue();
    }

    @Test
    void markAsReadIfUnread는_본인_소유가_아니면_갱신하지_않는다() {
        Long ownerId = insertUser("mark-owner@test.com");
        Long otherUserId = insertUser("mark-other@test.com");
        Notification saved = saveNotification(ownerId, NotificationType.PRICE_TARGET, "owned");

        int result = notificationRepository.markAsReadIfUnread(saved.getId(), otherUserId);

        assertThat(result).isEqualTo(0);
        assertThat(notificationRepository.findById(saved.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void deleteByIdAndUserId는_본인_소유_알림을_1건_삭제한다() {
        Long userId = insertUser("delete-owner@test.com");
        Notification saved = saveNotification(userId, NotificationType.PRICE_TARGET, "to-delete");

        int result = notificationRepository.deleteByIdAndUserId(saved.getId(), userId);

        assertThat(result).isEqualTo(1);
        assertThat(notificationRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deleteByIdAndUserId는_본인_소유가_아니면_삭제하지_않는다() {
        Long ownerId = insertUser("delete-owner2@test.com");
        Long otherUserId = insertUser("delete-other@test.com");
        Notification saved = saveNotification(ownerId, NotificationType.PRICE_TARGET, "owned");

        int result = notificationRepository.deleteByIdAndUserId(saved.getId(), otherUserId);

        assertThat(result).isEqualTo(0);
        assertThat(notificationRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void deleteByIdAndUserId는_존재하지_않는_알림이면_0건이다() {
        Long userId = insertUser("delete-none@test.com");

        int result = notificationRepository.deleteByIdAndUserId(999_999L, userId);

        assertThat(result).isEqualTo(0);
    }

    @Test
    void deleteExpiredNotifications_읽은_알림은_기준일이_지나면_삭제되고_그_전이면_유지된다() {
        Long userId = insertUser("cleanup-read@test.com");
        Notification oldRead = saveNotification(userId, NotificationType.PRICE_TARGET, "old-read");
        Notification recentRead = saveNotification(userId, NotificationType.PRICE_TARGET, "recent-read");
        notificationRepository.markAsReadIfUnread(oldRead.getId(), userId);
        notificationRepository.markAsReadIfUnread(recentRead.getId(), userId);
        entityManager.clear();
        backdateCreatedAt(oldRead.getId(), LocalDateTime.now().minusDays(31));
        backdateCreatedAt(recentRead.getId(), LocalDateTime.now().minusDays(29));

        int deleted = notificationRepository.deleteExpiredNotifications(
                LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(180));

        assertThat(deleted).isEqualTo(1);
        assertThat(notificationRepository.findById(oldRead.getId())).isEmpty();
        assertThat(notificationRepository.findById(recentRead.getId())).isPresent();
    }

    @Test
    void deleteExpiredNotifications_안읽은_알림은_읽은_기준일이_지나도_유지되고_더_긴_기준일이_지나야_삭제된다() {
        Long userId = insertUser("cleanup-unread@test.com");
        Notification oldUnread = saveNotification(userId, NotificationType.PRICE_TARGET, "old-unread");
        // 30일(읽은 알림 기준)은 지났지만 180일(안 읽은 알림 기준)은 안 지난 케이스 - 하이브리드 정책의 핵심.
        Notification recentUnread = saveNotification(userId, NotificationType.PRICE_TARGET, "recent-unread");
        backdateCreatedAt(oldUnread.getId(), LocalDateTime.now().minusDays(181));
        backdateCreatedAt(recentUnread.getId(), LocalDateTime.now().minusDays(60));

        int deleted = notificationRepository.deleteExpiredNotifications(
                LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(180));

        assertThat(deleted).isEqualTo(1);
        assertThat(notificationRepository.findById(oldUnread.getId())).isEmpty();
        assertThat(notificationRepository.findById(recentUnread.getId())).isPresent();
    }

    private void backdateCreatedAt(Long notificationId, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE notifications SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", notificationId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 문의_처리_완료_알림은_inquiry_id_컬럼에_참조_문의를_저장하고_그대로_조회된다() {
        Long userId = insertUser("inquiry@test.com");
        Long inquiryId = insertInquiry(userId);

        Notification saved = notificationRepository.save(
                Notification.builder()
                        .userId(userId)
                        .type(NotificationType.INQUIRY_HANDLED)
                        .message("'제목' 문의가 처리 완료되었습니다.")
                        .inquiryId(inquiryId)
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getInquiryId()).isEqualTo(inquiryId);
        assertThat(found.get().getCardId()).isNull();
    }

    // ===== #392: 이번에 추가한 알림 타입 5종의 저장·조회 왕복 =====
    // 서비스 단위 테스트(NotificationServiceTest)는 전부 mock이라 "새 enum 값이 실제 DB에 들어갔다가
    // 그대로 돌아오는지"를 아무도 확인하지 않았다. type 컬럼이 VARCHAR(30)이고 @Enumerated(STRING)이라
    // 이름이 길어지거나 매핑이 어긋나면 런타임에서야 드러나므로, 여기서 실제 왕복으로 고정한다
    // (현재 가장 긴 값은 TRADE_SHIPPING_REQUIRED = 23자).

    @Test
    void 이번에_추가한_알림_타입_5종이_모두_저장되고_findByUserId로_그대로_조회된다() {
        Long userId = insertUser("new-types@test.com");
        List<NotificationType> addedTypes = List.of(
                NotificationType.INQUIRY_RECEIVED,
                NotificationType.TRADE_SHIPPING_REQUIRED,
                NotificationType.TRADE_DELIVERED,
                NotificationType.TRADE_CANCELLED,
                NotificationType.BUY_OFFER_MATCHED);
        addedTypes.forEach(type -> saveNotification(userId, type, type.name() + " 메시지"));
        entityManager.clear();

        Page<Notification> found = notificationRepository.findByUserId(
                userId, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(found.getContent()).extracting(Notification::getType)
                .containsExactlyElementsOf(addedTypes);
    }

    @Test
    void 새_문의_도착_알림은_inquiry_id를_저장하고_그대로_조회된다() {
        Long adminId = insertUser("admin-received@test.com");
        Long inquiryId = insertInquiry(adminId);

        Notification saved = notificationRepository.save(
                Notification.builder()
                        .userId(adminId)
                        .type(NotificationType.INQUIRY_RECEIVED)
                        .message("새 문의가 등록되었습니다: '제목'")
                        .inquiryId(inquiryId)
                        .build()
        );
        entityManager.flush();
        entityManager.clear();

        Optional<Notification> found = notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(NotificationType.INQUIRY_RECEIVED);
        assertThat(found.get().getInquiryId()).isEqualTo(inquiryId);
        // 관리자에게 가는 문의 알림은 카드와 무관하다.
        assertThat(found.get().getCardId()).isNull();
    }

    @Test
    void 거래_알림_4종은_card_id를_저장하고_그대로_조회된다() {
        Long userId = insertUser("trade-types@test.com");
        Long cardId = insertCard("notif-trade-card");
        List<NotificationType> tradeTypes = List.of(
                NotificationType.TRADE_SHIPPING_REQUIRED,
                NotificationType.TRADE_DELIVERED,
                NotificationType.TRADE_CANCELLED,
                NotificationType.BUY_OFFER_MATCHED);

        for (NotificationType type : tradeTypes) {
            Notification saved = notificationRepository.save(
                    Notification.builder()
                            .userId(userId)
                            .type(type)
                            .message(type.name() + " 메시지")
                            .cardId(cardId)
                            .build()
            );
            entityManager.flush();
            entityManager.clear();

            Optional<Notification> found = notificationRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getType()).as("타입 %s", type).isEqualTo(type);
            // card_id는 cards(id)를 참조하는 FK라, 실재하지 않는 id를 넣으면 여기서 제약 위반으로 드러난다.
            assertThat(found.get().getCardId()).as("타입 %s의 cardId", type).isEqualTo(cardId);
            // 거래 알림은 문의와 무관하므로 inquiry_id는 비어 있어야 한다.
            assertThat(found.get().getInquiryId()).as("타입 %s의 inquiryId", type).isNull();
        }
    }

    private Long insertCard(String externalId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO cards (external_id, name) VALUES (:externalId, 'Notification Test Card') RETURNING id")
                .setParameter("externalId", externalId)
                .getSingleResult()).longValue();
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

    private Long insertInquiry(Long userId) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO inquiries (user_id, category, status, title, content) "
                                + "VALUES (:userId, 'ETC', 'HANDLED', '제목', '내용') RETURNING id")
                .setParameter("userId", userId)
                .getSingleResult()).longValue();
    }

    private Long insertUser(String email) {
        return ((Number) entityManager.createNativeQuery(
                        "INSERT INTO users (email, nickname, provider, role, status) "
                                + "VALUES (:email, :nickname, 'LOCAL', 'USER', 'ACTIVE') RETURNING id")
                .setParameter("email", email)
                .setParameter("nickname", email.substring(0, email.indexOf('@')))
                .getSingleResult()).longValue();
    }
}
