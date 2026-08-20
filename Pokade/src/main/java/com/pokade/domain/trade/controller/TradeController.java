package com.pokade.domain.trade.controller;

import com.pokade.domain.trade.dto.TradePaymentConfirmRequest;
import com.pokade.domain.trade.dto.TradeReadyRequest;
import com.pokade.domain.trade.dto.TradeReadyResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "거래", description = "즉시구매 및 거래 상태(발송/검수/배송/확정/취소) 관리 API")
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @Operation(
            summary = "즉시구매 결제 준비",
            description = "매물 즉시구매를 위한 토스페이먼츠 결제창을 띄우기 전, 주문을 PENDING으로 먼저 기록합니다. "
                    + "이 시점에는 매물을 잠그지 않습니다 - 본인이 등록한 매물이면 실패합니다."
    )
    @PostMapping("/ready")
    public ApiResponse<TradeReadyResponse> ready(
            @AuthenticationPrincipal Long buyerId,
            @Valid @RequestBody TradeReadyRequest request
    ) {
        return ApiResponse.ok(tradeService.ready(buyerId, request));
    }

    @Operation(
            summary = "즉시구매 결제 승인",
            description = "토스페이먼츠 결제창 successUrl 리다이렉트 이후 호출합니다. 결제 승인 후 매물을 잠그고 "
                    + "거래를 생성합니다. 그 사이 다른 구매자가 먼저 구매했다면 승인된 결제를 즉시 취소(환불)하고 실패합니다."
    )
    @PostMapping("/confirm-payment")
    public ApiResponse<TradeResponse> confirmPurchase(
            @AuthenticationPrincipal Long buyerId,
            @Valid @RequestBody TradePaymentConfirmRequest request
    ) {
        TradeResponse response = tradeService.confirmPurchase(
                buyerId, request.paymentKey(), request.orderId(), request.amount());
        return ApiResponse.ok("구매가 완료되었습니다.", response);
    }

    @Operation(summary = "거래 상세 조회", description = "거래 참여자(구매자 또는 판매자)만 조회할 수 있습니다.")
    @GetMapping("/{id}")
    public ApiResponse<TradeResponse> getTrade(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "거래 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(tradeService.getTrade(userId, id));
    }

    @Operation(
            summary = "판매자 발송 처리",
            description = "판매자가 매물을 플랫폼으로 발송했음을 기록합니다 (PENDING → SHIPPED_TO_PLATFORM). 판매자 본인만 호출할 수 있습니다."
    )
    @PatchMapping("/{id}/ship")
    public ApiResponse<TradeResponse> shipTrade(
            @AuthenticationPrincipal Long sellerId,
            @Parameter(description = "거래 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok("발송 처리되었습니다.", tradeService.shipTrade(sellerId, id));
    }

    @Operation(
            summary = "구매 확정",
            description = "구매자가 수령한 물품을 확인하고 거래를 최종 확정합니다 (DELIVERED → COMPLETED). "
                    + "배송 완료(DELIVERED) 상태가 아니면 실패합니다."
    )
    @PatchMapping("/{id}/confirm")
    public ApiResponse<TradeResponse> confirmTrade(
            @AuthenticationPrincipal Long buyerId,
            @Parameter(description = "거래 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok("구매가 확정되었습니다.", tradeService.confirmTrade(buyerId, id));
    }

    @Operation(summary = "거래 취소", description = "최종 상태(COMPLETED/CANCELLED)가 아닌 거래를 취소합니다.")
    @PatchMapping("/{id}/cancel")
    public ApiResponse<TradeResponse> cancelTrade(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "거래 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok("거래가 취소되었습니다.", tradeService.cancelTrade(userId, id));
    }
}
