package com.pokade.domain.analytics.controller;

import com.pokade.domain.analytics.service.SiteVisitService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방문 통계", description = "사이트 방문 집계 API")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class SiteVisitController {

    private final SiteVisitService siteVisitService;

    // FE가 브라우저 세션당 1회 호출 - 로그인 여부와 무관하게 누구나 호출 가능해야 하는 공개 엔드포인트.
    @Operation(
            summary = "방문 기록",
            description = "사이트 방문을 1회 집계합니다. 프론트가 브라우저 세션당 한 번 호출하며, "
                    + "로그인 여부와 무관하게 누구나 호출할 수 있습니다."
    )
    @PostMapping("/visits")
    public ApiResponse<Void> recordVisit() {
        siteVisitService.recordVisit();
        return ApiResponse.ok("기록되었습니다.");
    }
}
