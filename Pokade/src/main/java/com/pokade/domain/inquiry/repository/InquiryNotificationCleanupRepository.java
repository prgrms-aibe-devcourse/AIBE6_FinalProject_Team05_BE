package com.pokade.domain.inquiry.repository;

import com.pokade.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// domain.notification의 Notification 엔티티를 domain.inquiry가 직접 건드릴 일이 있어 추가한
// 전용 리포지토리 - JpaRepository가 아니라 Spring Data의 맨몸 Repository<T, ID>를 상속해,
// 실수로 다른 도메인 소유 테이블에 save()/delete()를 호출할 수 없게 원천 차단한다
// (PriceTradeStatsRepository와 동일한 패턴 - "Package convention" 참고).
// 문의를 삭제할 때(InquiryService.deleteInquiry), 문의 등록 시 관리자에게 보낸
// INQUIRY_RECEIVED 알림이 notifications.inquiry_id로 그 문의를 참조하고 있어 FK 제약에
// 걸린다 - 이 알림도 함께 정리해야 한다.
public interface InquiryNotificationCleanupRepository extends Repository<Notification, Long> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.inquiryId = :inquiryId")
    void deleteByInquiryId(@Param("inquiryId") Long inquiryId);
}
