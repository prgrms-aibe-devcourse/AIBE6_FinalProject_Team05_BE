package com.pokade.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    // User 엔티티는 별도 담당자 파트 — FK만 보관 (비로그인 질문은 null)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public ChatMessage(String sessionId, Long userId, ChatRole role, String content) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role.name();
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    // 비로그인 히스토리 이관(ChatService.importHistory) 전용 - 실제 저장 시각이 아니라 클라이언트가 그 프리셋을
    // 눌렀던 시각(askedAt)을 createdAt으로 남겨야 히스토리 정렬이 실제 대화 순서와 맞는다.
    @Builder(builderMethodName = "importedBuilder")
    public ChatMessage(String sessionId, Long userId, ChatRole role, String content, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role.name();
        this.content = content;
        this.createdAt = createdAt;
    }

    public ChatRole getRoleEnum() {
        return ChatRole.valueOf(role);
    }
}
