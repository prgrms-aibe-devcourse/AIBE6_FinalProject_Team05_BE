package com.pokade.domain.notification.controller;

import com.pokade.domain.notification.dto.NotificationResponse;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "알림", description = "알림 목록 조회·읽음 처리·삭제 및 SSE 실시간 구독 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "알림 목록 조회",
            description = "로그인한 회원의 알림을 최신순으로 페이징 조회합니다."
    )
    @GetMapping
    public ApiResponse<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(notificationService.getNotifications(userId, pageable));
    }

    @Operation(
            summary = "알림 읽음 처리",
            description = "알림 한 건을 읽음 상태로 바꿉니다. 본인 알림만 처리할 수 있습니다."
    )
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "알림 ID") @PathVariable Long id
    ) {
        notificationService.markAsRead(userId, id);
        return ApiResponse.ok("알림을 읽음 처리했습니다.");
    }

    @Operation(
            summary = "알림 삭제",
            description = "알림 한 건을 삭제합니다. 본인 알림만 삭제할 수 있습니다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "알림 ID") @PathVariable Long id
    ) {
        notificationService.deleteNotification(userId, id);
        return ApiResponse.ok("알림을 삭제했습니다.");
    }

    @Operation(
            summary = "실시간 알림 구독 (SSE)",
            description = "Server-Sent Events로 실시간 알림을 구독합니다. 인증이 필요하며 액세스 토큰을 "
                    + "Authorization: Bearer 헤더로 보내야 합니다. 브라우저 기본 EventSource는 요청 헤더를 "
                    + "지정할 수 없어 이 엔드포인트에 연결할 수 없고, 헤더를 붙일 수 있는 fetch 기반 SSE "
                    + "클라이언트를 써야 합니다(프론트엔드는 @microsoft/fetch-event-source를 사용합니다). "
                    + "응답이 스트림이라 Swagger UI의 Try it out으로는 정상 확인이 어렵습니다."
    )
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long userId) {
        return notificationService.subscribe(userId);
    }
}
