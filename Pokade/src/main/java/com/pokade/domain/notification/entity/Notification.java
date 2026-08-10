package com.pokade.domain.notification.entity;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
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
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, NotificationType type, String message) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.isRead = false;
    }

    public void markAsRead() {
        requireUnread();
        this.isRead = true;
    }

    private void requireUnread() {
        if (this.isRead) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ALREADY_READ);
        }
    }
}
