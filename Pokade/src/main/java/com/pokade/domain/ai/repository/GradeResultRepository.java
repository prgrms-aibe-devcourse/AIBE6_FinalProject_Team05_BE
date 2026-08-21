package com.pokade.domain.ai.repository;

import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.ai.entity.GradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GradeResultRepository extends JpaRepository<GradeResult, Long> {

    // 진단 이력 조회: 본인 이력만 페이징 조회
    Page<GradeResult> findByUserId(Long userId, Pageable pageable);

    // 무료 사용 횟수: 정상 산출(SUCCESS) 건만 카운트
    long countByUserIdAndStatus(Long userId, GradeStatus status);

    // 재업로드 권한을 원자적으로 획득: 조건 확인 + retryUsed=true 마킹을 단일 UPDATE로 처리.
    // 반환값이 1이면 클레임 성공(무료 재시도 허용), 0이면 이미 사용됐거나 조건 불일치.
    // check-then-act 경쟁 조건을 DB 레벨에서 방지한다.
    @Modifying
    @Transactional
    @Query("UPDATE GradeResult g SET g.retryUsed = true WHERE g.id = :id AND g.userId = :userId " +
           "AND g.status = :status AND g.retryAllowed = true AND g.retryUsed = false")
    int claimRetry(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("status") GradeStatus status);
}
