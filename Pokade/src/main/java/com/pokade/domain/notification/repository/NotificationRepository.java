package com.pokade.domain.notification.repository;

import com.pokade.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 프로덕션 코드에서는 쓰지 않는다 - 페이지네이션 없이 유저의 알림 전체를 최신순으로 가져오는
    // 테스트 전용 헬퍼다. 워치리스트 알림 통합/동시성 테스트(WatchlistListingAvailableNoticeIntegrationTest,
    // WatchlistTargetPriceNoticeConcurrencyTest, WatchlistTargetPriceNoticeTransactionIsolationTest)가
    // "알림이 실제로 몇 건, 어떤 내용으로 생성됐는지" 검증하거나 테스트 종료 후 정리(deleteAll)할 때 쓴다.
    // 실제 GET /api/notifications API는 아래 findByUserId(페이징)를 쓴다.
    // 명시적 @Query가 필요한 이유: 메서드 이름 파생 쿼리 규칙상 "ForTestVerification"이 유효한
    // 프로퍼티/키워드로 해석되지 않아 PropertyReferenceException이 난다 - 이름을 자유롭게 짓기 위해
    // JPQL을 직접 명시했다(기존 파생 쿼리와 동일한 쿼리를 그대로 재현).
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId ORDER BY n.createdAt DESC")
    List<Notification> findAllByUserIdForTestVerification(@Param("userId") Long userId);

    // #162: GET /api/notifications가 실제로 쓰는 페이지네이션 조회 메서드.
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // 읽음 처리를 조회-후-갱신이 아니라 조건부 원자적 UPDATE로 수행한다. 동시에 두 요청이 들어와도
    // is_read=false 조건 자체가 DB 행 잠금과 함께 평가되므로 정확히 하나만 1을 반환한다(TOCTOU 방지).
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId AND n.isRead = false")
    int markAsReadIfUnread(@Param("id") Long id, @Param("userId") Long userId);

    // #162: 개별 삭제도 markAsReadIfUnread와 같은 이유로 조회-후-삭제 대신 조건부 원자적 DELETE로 처리한다.
    // 삭제된 행 수가 0이면(존재하지 않거나 본인 소유가 아님) 호출부가 NOTIFICATION_NOT_FOUND로 처리한다 -
    // 본인 소유가 아닌 알림의 존재 여부를 별도로 노출하지 않는다.
    // clearAutomatically: 벌크 DELETE는 영속성 컨텍스트를 거치지 않아, 이미 로드되어 관리 중인 엔티티가
    // 있으면 DB에서 지워진 뒤에도 1차 캐시 때문에 findById 등에서 계속 조회되는 문제가 있어 자동으로 비운다.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // #162: 자동 정리(하이브리드 TTL) - 읽은 알림은 readCutoff보다 오래되면 삭제, 안 읽은 알림도
    // unreadCutoff(더 긴 유예기간)보다 오래되면 삭제한다 - 안 읽은 알림이 영원히 안 지워지는 것을 방지한다.
    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM Notification n
            WHERE (n.isRead = true AND n.createdAt < :readCutoff)
               OR (n.isRead = false AND n.createdAt < :unreadCutoff)
            """)
    int deleteExpiredNotifications(@Param("readCutoff") LocalDateTime readCutoff,
                                    @Param("unreadCutoff") LocalDateTime unreadCutoff);
}
