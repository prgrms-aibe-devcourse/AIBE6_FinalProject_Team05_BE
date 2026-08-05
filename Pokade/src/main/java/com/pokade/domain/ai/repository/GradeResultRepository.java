package com.pokade.domain.ai.repository;

import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.ai.entity.GradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GradeResultRepository extends JpaRepository<GradeResult, Long> {

    // 진단 이력 조회: 본인 이력만 페이징 조회
    Page<GradeResult> findByUserId(Long userId, Pageable pageable);

    // 무료 사용 횟수: 정상 산출(SUCCESS) 건만 카운트
    long countByUserIdAndStatus(Long userId, GradeStatus status);

    // 재업로드 가능 여부: QUALITY_FAIL이고 아직 retry_used=false인 건
    @Query("SELECT COUNT(g) > 0 FROM GradeResult g WHERE g.id = :id AND g.userId = :userId " +
           "AND g.status = 'QUALITY_FAIL' AND g.retryAllowed = true AND g.retryUsed = false")
    boolean existsRetryableResult(Long id, Long userId);
}
