package com.pokade.domain.notification.controller;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(notificationService.getNotifications(userId, pageable));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        notificationService.markAsRead(userId, id);
        return ApiResponse.ok("알림을 읽음 처리했습니다.");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        notificationService.deleteNotification(userId, id);
        return ApiResponse.ok("알림을 삭제했습니다.");
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long userId) {
        return notificationService.subscribe(userId);
    }
}
