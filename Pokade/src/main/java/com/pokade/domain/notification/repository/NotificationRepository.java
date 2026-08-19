package com.pokade.domain.notification.repository;

import com.pokade.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // #162: 목록 조회 페이지네이션용. 위 findByUserIdOrderByCreatedAtDesc()는 워치리스트 알림 테스트
    // (WatchlistTargetPriceNoticeConcurrencyTest 등)가 검증/정리 용도로 그대로 쓰고 있어 건드리지 않는다.
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
}
