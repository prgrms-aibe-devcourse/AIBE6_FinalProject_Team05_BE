package com.pokade.domain.ai.dto;

import com.pokade.domain.ai.entity.GradeResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POST /api/ai/grade 응답 DTO
 *
 * TODO: User 파트 개발 완료 후 remainingPoints(차감 후 잔여 포인트) 필드 추가 예정
 */
public record GradeResponse(
        Long gradeResultId,
        String status,           // SUCCESS / QUALITY_FAIL
        String grade,            // S / A / B (QUALITY_FAIL이면 null)
        BigDecimal centeringScore,
        BigDecimal edgeScore,
        BigDecimal surfaceScore,
        BigDecimal cornerScore,
        BigDecimal confidence,
        boolean isFree,
        int pointUsed,
        boolean retryAllowed,    // QUALITY_FAIL 시 무료 재업로드 가능 여부
        String notice,           // 법적 고지 문구
        LocalDateTime createdAt
) {
    private static final String LEGAL_NOTICE =
            "본 결과는 AI 기반 참고용 예비진단이며, 정식 카드 감정을 대체하지 않습니다.";

    public static GradeResponse from(GradeResult result) {
        return new GradeResponse(
                result.getId(),
                result.getStatus().name(),
                result.getGrade(),
                result.getCenteringScore(),
                result.getEdgeScore(),
                result.getSurfaceScore(),
                result.getCornerScore(),
                result.getConfidence(),
                result.isFree(),
                result.getPointUsed(),
                result.isRetryAllowed(),
                LEGAL_NOTICE,
                result.getCreatedAt()
        );
    }
}
