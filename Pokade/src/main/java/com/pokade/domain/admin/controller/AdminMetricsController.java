package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import com.pokade.domain.admin.metrics.service.AdminMetricsService;
import com.pokade.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    // 어드민 대시보드 지표 카드 + 시계열 차트 데이터를 한 번에 반환한다. period는 차트(시리즈)에만 적용되고
    // 카드는 항상 고정된 "오늘/24h" 스냅샷이다. Prometheus/Grafana는 이 응답의 데이터 원본일 뿐 화면에는
    // 노출되지 않는다 - FE가 자체 컴포넌트로 그린다.
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @RequestParam(defaultValue = "1h") String period) {
        return ApiResponse.ok(adminMetricsService.getDashboard(period));
    }
}
