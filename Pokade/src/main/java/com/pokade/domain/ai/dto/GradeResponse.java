package com.pokade.domain.ai.dto;

import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.card.entity.Card;

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
        LocalDateTime createdAt,
        // vision_card_id(externalId)로 해석된 카드 — 자체 DB에 없는 카드거나 인식 실패 시
        // cardId/cardName/cardImageSmall만 전부 null(cardConfidence는 별도로 채워질 수 있음).
        // FR-AI-04(도감 등록) 진입 가능 여부를 FE가 판단하는 기준이기도 하다.
        Long cardId,
        String cardName,
        String cardImageSmall,
        // 카드 인식 신뢰도(%) — 등급 산출 신뢰도(confidence)와는 별개 지표.
        BigDecimal cardConfidence
) {
    private static final String LEGAL_NOTICE =
            "본 결과는 AI 기반 참고용 예비진단이며, 정식 카드 감정을 대체하지 않습니다.";

    public static GradeResponse from(GradeResult result, Card card) {
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
                result.getCreatedAt(),
                card != null ? card.getId() : null,
                card != null ? card.getName() : null,
                card != null ? card.getImageSmall() : null,
                result.getVisionConfidence()
        );
    }
}
