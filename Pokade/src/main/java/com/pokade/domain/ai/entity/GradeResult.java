package com.pokade.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_results")
@Getter
@NoArgsConstructor
public class GradeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User 엔티티는 별도 담당자 파트 — FK만 보관
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "variant_id")
    private Long variantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeStatus status;

    @Column(length = 10)
    private String grade;

    @Column(name = "centering_score", precision = 6, scale = 2)
    private BigDecimal centeringScore;

    @Column(name = "edge_score", precision = 6, scale = 2)
    private BigDecimal edgeScore;

    @Column(name = "surface_score", precision = 6, scale = 2)
    private BigDecimal surfaceScore;

    @Column(name = "corner_score", precision = 6, scale = 2)
    private BigDecimal cornerScore;

    @Column(name = "is_free", nullable = false)
    private boolean isFree;

    // TODO: User 파트 개발 완료 후 포인트 차감 연동 예정
    @Column(name = "point_used", nullable = false)
    private int pointUsed;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "vision_card_id", length = 50)
    private String visionCardId;

    @Column(name = "vision_confidence", precision = 5, scale = 2)
    private BigDecimal visionConfidence;

    @Column(name = "retry_allowed", nullable = false)
    private boolean retryAllowed;

    @Column(name = "retry_used", nullable = false)
    private boolean retryUsed;

    @Column(name = "retry_of_id")
    private Long retryOfId;

    // 제출 이미지 6장의 SHA-256 해시 — 같은 이미지로 재요청 시 Vision을 다시 부르지 않고 이 값으로 캐시
    // 조회한다(등급 비일관성 방지 + 비용 절감). 기존 행은 마이그레이션 이전 데이터라 null일 수 있다.
    @Column(name = "image_hash", length = 64)
    private String imageHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public GradeResult(Long userId, Long cardId, Long variantId, GradeStatus status,
                       String grade, BigDecimal centeringScore, BigDecimal edgeScore,
                       BigDecimal surfaceScore, BigDecimal cornerScore, boolean isFree,
                       int pointUsed, BigDecimal confidence, String visionCardId,
                       BigDecimal visionConfidence, boolean retryAllowed, Long retryOfId,
                       String imageHash) {
        this.userId = userId;
        this.cardId = cardId;
        this.variantId = variantId;
        this.status = status;
        this.grade = grade;
        this.centeringScore = centeringScore;
        this.edgeScore = edgeScore;
        this.surfaceScore = surfaceScore;
        this.cornerScore = cornerScore;
        this.isFree = isFree;
        this.pointUsed = pointUsed;
        this.confidence = confidence;
        this.visionCardId = visionCardId;
        this.visionConfidence = visionConfidence;
        this.retryAllowed = retryAllowed;
        this.retryUsed = false;
        this.retryOfId = retryOfId;
        this.imageHash = imageHash;
        this.createdAt = LocalDateTime.now();
    }

    public void markRetryUsed() {
        this.retryUsed = true;
    }

}
