package com.pokade.domain.point.controller;

import com.pokade.domain.point.dto.request.PointChargeConfirmRequest;
import com.pokade.domain.point.dto.request.PointChargeReadyRequest;
import com.pokade.domain.point.dto.response.PointChargeConfirmResponse;
import com.pokade.domain.point.dto.response.PointChargeReadyResponse;
import com.pokade.domain.point.service.PointChargeService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트", description = "토스페이먼츠 연동 포인트 충전 API")
@RestController
@RequestMapping("/api/points/charge")
@RequiredArgsConstructor
public class PointChargeController {

    private final PointChargeService pointChargeService;

    @Operation(
            summary = "포인트 충전 준비",
            description = "결제창을 띄우기 전 충전 주문을 PENDING으로 먼저 기록합니다. 이 시점에는 포인트가 늘지 않습니다."
    )
    @PostMapping("/ready")
    public ApiResponse<PointChargeReadyResponse> ready(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PointChargeReadyRequest request) {
        return ApiResponse.ok(pointChargeService.ready(userId, request));
    }

    @Operation(
            summary = "포인트 충전 승인",
            description = "결제창 successUrl 리다이렉트 이후 호출합니다. 결제를 승인하고 포인트를 적립한 뒤 충전 후 "
                    + "잔액을 반환합니다. 본인 주문이 아니거나, 이미 처리된 주문이거나, 요청 금액이 준비 단계의 "
                    + "주문 금액과 다르면 결제 승인을 시도하지 않고 거부합니다."
    )
    @PostMapping("/confirm")
    public ApiResponse<PointChargeConfirmResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PointChargeConfirmRequest request) {
        int balance = pointChargeService.confirm(userId, request.paymentKey(), request.orderId(), request.amount());
        return ApiResponse.ok("포인트가 충전되었습니다.", new PointChargeConfirmResponse(balance));
    }
}
