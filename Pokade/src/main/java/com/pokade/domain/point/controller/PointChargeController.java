package com.pokade.domain.point.controller;

import com.pokade.domain.point.dto.request.PointChargeConfirmRequest;
import com.pokade.domain.point.dto.request.PointChargeReadyRequest;
import com.pokade.domain.point.dto.response.PointChargeConfirmResponse;
import com.pokade.domain.point.dto.response.PointChargeReadyResponse;
import com.pokade.domain.point.service.PointChargeService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points/charge")
@RequiredArgsConstructor
public class PointChargeController {

    private final PointChargeService pointChargeService;

    @PostMapping("/ready")
    public ApiResponse<PointChargeReadyResponse> ready(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PointChargeReadyRequest request) {
        return ApiResponse.ok(pointChargeService.ready(userId, request));
    }

    @PostMapping("/confirm")
    public ApiResponse<PointChargeConfirmResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PointChargeConfirmRequest request) {
        int balance = pointChargeService.confirm(userId, request.paymentKey(), request.orderId(), request.amount());
        return ApiResponse.ok("포인트가 충전되었습니다.", new PointChargeConfirmResponse(balance));
    }
}
