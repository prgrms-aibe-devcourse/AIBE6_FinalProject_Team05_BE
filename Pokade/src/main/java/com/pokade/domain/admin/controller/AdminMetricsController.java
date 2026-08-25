package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.metrics.dto.AdminDashboardResponse;
import com.pokade.domain.admin.metrics.service.AdminMetricsService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 지표", description = "어드민 대시보드 지표·시계열 조회 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    // 어드민 대시보드 지표 카드 + 시계열 차트 데이터를 한 번에 반환한다. period는 차트(시리즈)에만 적용되고
    // 카드는 항상 고정된 "오늘/24h" 스냅샷이다. Prometheus/Grafana는 이 응답의 데이터 원본일 뿐 화면에는
    // 노출되지 않는다 - FE가 자체 컴포넌트로 그린다.
    @Operation(
            summary = "대시보드 지표 조회",
            description = "어드민 대시보드의 지표 카드와 시계열 차트 데이터를 한 번에 반환합니다. period는 차트 "
                    + "시리즈에만 적용되고, 카드 값은 항상 오늘/24시간 기준 스냅샷입니다."
    )
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @Parameter(description = "차트 조회 기간 (예: 1h)") @RequestParam(defaultValue = "1h") String period) {
        return ApiResponse.ok(adminMetricsService.getDashboard(period));
    }
}
