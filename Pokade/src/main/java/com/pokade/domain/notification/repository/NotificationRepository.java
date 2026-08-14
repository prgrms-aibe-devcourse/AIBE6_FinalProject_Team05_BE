package com.pokade.domain.notification.repository;

import com.pokade.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // 읽음 처리를 조회-후-갱신이 아니라 조건부 원자적 UPDATE로 수행한다. 동시에 두 요청이 들어와도
    // is_read=false 조건 자체가 DB 행 잠금과 함께 평가되므로 정확히 하나만 1을 반환한다(TOCTOU 방지).
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId AND n.isRead = false")
    int markAsReadIfUnread(@Param("id") Long id, @Param("userId") Long userId);
}
