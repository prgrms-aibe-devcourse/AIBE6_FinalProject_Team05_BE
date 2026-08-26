package com.pokade.domain.admin.controller;

import com.pokade.domain.admin.dto.response.AdminTradeResponse;
import com.pokade.domain.admin.service.AdminTradeService;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "관리자 - 거래", description = "거래 검수/배송 처리 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/admin/trades")
@RequiredArgsConstructor
public class AdminTradeController {

    private final TradeService tradeService;
    private final AdminTradeService adminTradeService;

    @Operation(
            summary = "검수/배송 대기 거래 목록 조회",
            description = "발송됨(SHIPPED_TO_PLATFORM, 검수 대기)·검수됨(INSPECTED, 배송 대기) 거래를 접수순으로 조회합니다."
    )
    @GetMapping
    public ApiResponse<List<AdminTradeResponse>> getPendingTrades() {
        return ApiResponse.ok(adminTradeService.getPendingTrades());
    }

    @Operation(summary = "거래 상세 조회", description = "거래 번호로 거래 상세(현재 진행 상황)를 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<AdminTradeResponse> getTrade(@Parameter(description = "거래 ID") @PathVariable Long id) {
        return ApiResponse.ok(adminTradeService.getTrade(id));
    }

    @Operation(summary = "검수 완료 처리", description = "발송된 거래를 검수 완료 처리합니다 (SHIPPED_TO_PLATFORM → INSPECTED).")
    @PatchMapping("/{id}/inspect")
    public ApiResponse<TradeResponse> inspectTrade(@Parameter(description = "거래 ID") @PathVariable Long id) {
        return ApiResponse.ok("검수 완료 처리되었습니다.", tradeService.markInspected(id));
    }

    @Operation(summary = "배송 완료 처리", description = "검수된 거래를 배송 완료 처리합니다 (INSPECTED → DELIVERED).")
    @PatchMapping("/{id}/deliver")
    public ApiResponse<TradeResponse> deliverTrade(@Parameter(description = "거래 ID") @PathVariable Long id) {
        return ApiResponse.ok("배송 완료 처리되었습니다.", tradeService.markDelivered(id));
    }
}
