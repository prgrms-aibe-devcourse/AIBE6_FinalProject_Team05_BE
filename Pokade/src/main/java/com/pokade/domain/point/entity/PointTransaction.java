package com.pokade.domain.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 포인트 충전/사용/환불 이력. users.point_balance 변경은 항상 이 테이블에 한 행을 남기며 기록한다.
@Entity
@Table(name = "point_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionType type;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    // AI 등급진단 유료 사용 시의 차감 이력용 (domain.ai 소관, 현재는 아무 코드도 이 값을 채우지 않는다)
    @Column(name = "related_grade_result_id")
    private Long relatedGradeResultId;

    // 매물 구매로 인한 차감 이력용
    @Column(name = "related_trade_id")
    private Long relatedTradeId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PointTransaction(Long userId, PointTransactionType type, Integer amount, Integer balanceAfter,
                             Long relatedGradeResultId, Long relatedTradeId) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.relatedGradeResultId = relatedGradeResultId;
        this.relatedTradeId = relatedTradeId;
    }
}
