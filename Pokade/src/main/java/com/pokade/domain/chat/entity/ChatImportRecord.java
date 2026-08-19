package com.pokade.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 비로그인 상태에서 눌렀던 프리셋(급등/급락)을 로그인 후 채팅 히스토리로 이관할 때의 멱등성 마커.
// (userId, sessionId, presetId, askedAt) 조합이 이미 있으면 같은 항목을 다시 이관하지 않는다 -
// 네트워크 재시도, 여러 탭 동시 로그인, 재로그인으로 인한 중복 저장을 막기 위함.
@Entity
@Table(
        name = "chat_import_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_id", "preset_id", "asked_at"})
)
@Getter
@NoArgsConstructor
public class ChatImportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "preset_id", nullable = false, length = 50)
    private String presetId;

    @Column(name = "asked_at", nullable = false)
    private LocalDateTime askedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public ChatImportRecord(Long userId, String sessionId, String presetId, LocalDateTime askedAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.presetId = presetId;
        this.askedAt = askedAt;
        this.createdAt = LocalDateTime.now();
    }
}
