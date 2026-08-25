package com.pokade.domain.inquiry.entity;

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

@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User 엔티티는 별도 담당자 파트 - FK만 보관
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Inquiry(Long userId, String title, String content, InquiryCategory category) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.status = InquiryStatus.UNHANDLED;
    }

    public void changeStatus(InquiryStatus status) {
        this.status = status;
    }

    // UNHANDLED 상태에서만 호출 가능하도록 서비스 계층에서 먼저 검증한다(답변 이후에는 이력 보존을
    // 위해 수정을 막는다 - 엔티티 자체는 그 정책을 모르고 값 변경만 담당).
    public void update(InquiryCategory category, String title, String content) {
        this.category = category;
        this.title = title;
        this.content = content;
    }

    public void answer(String answerContent) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.status = InquiryStatus.HANDLED;
    }
}
