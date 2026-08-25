package com.pokade.domain.ai.dto;

import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.card.entity.Card;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * POST /api/ai/grade 및 GET /api/ai/grade/{resultId} 응답 DTO
 *
 * imageUrls: PhotoType.name() → presigned URL (10분 유효).
 *            이력 목록(GET /api/ai/grade/history)에서는 null — 목록에서 이미지 로딩은 과도한 S3 호출을 유발한다.
 * remainingPoints: 포인트 차감 후 잔여 포인트. 무료 요청이거나 이력 목록에서는 null.
 * cached: 이번 요청이 동일 이미지 캐시로 재사용된 결과인지. true면 isFree/pointUsed는 "이번" 진단이
 *         아니라 원본 진단 때 값이 그대로 노출되는 것뿐이고 이번엔 아무 과금도 없었다는 뜻이다 - FE는
 *         이 플래그로 "이전과 동일한 진단 결과입니다" 같은 안내를 pointUsed와 구분해서 보여줘야 한다.
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
        // 카드 이름에서 종 이름 부분만 한글로 치환한 값 - domain.card.support.CardNameKoResolver가
        // 매핑을 못 찾으면(트레이너/에너지 카드 등) null. FE는 cardNameKo ?? cardName으로 표시한다.
        String cardNameKo,
        String cardImageSmall,
        // 카드 인식 신뢰도(%) — 등급 산출 신뢰도(confidence)와는 별개 지표.
        BigDecimal cardConfidence,
        // 제출 이미지 presigned URL — PhotoType.name() 키(FRONT/BACK/CORNER_TL/…).
        // 이력 목록에서는 null.
        Map<String, String> imageUrls,
        // 포인트 차감 후 잔여 포인트. 무료 요청이거나 이력 목록에서는 null.
        Integer remainingPoints,
        // 동일 이미지 캐시로 재사용된 결과인지 (위 클래스 주석 참고)
        boolean cached
) {
    private static final String LEGAL_NOTICE =
            "본 결과는 AI 기반 참고용 예비진단이며, 정식 카드 감정을 대체하지 않습니다.";

    public static GradeResponse from(GradeResult result, Card card, String cardNameKo,
                                      Map<String, String> imageUrls, Integer remainingPoints) {
        return from(result, card, cardNameKo, imageUrls, remainingPoints, false);
    }

    public static GradeResponse from(GradeResult result, Card card, String cardNameKo, Map<String, String> imageUrls,
                                      Integer remainingPoints, boolean cached) {
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
                card != null ? cardNameKo : null,
                card != null ? card.getImageSmall() : null,
                result.getVisionConfidence(),
                imageUrls,
                remainingPoints,
                cached
        );
    }
}
