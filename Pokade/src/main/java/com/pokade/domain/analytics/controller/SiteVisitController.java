package com.pokade.domain.analytics.controller;

import com.pokade.domain.analytics.service.SiteVisitService;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class SiteVisitController {

    private final SiteVisitService siteVisitService;

    // FE가 브라우저 세션당 1회 호출 - 로그인 여부와 무관하게 누구나 호출 가능해야 하는 공개 엔드포인트.
    @PostMapping("/visits")
    public ApiResponse<Void> recordVisit() {
        siteVisitService.recordVisit();
        return ApiResponse.ok("기록되었습니다.");
    }
}
