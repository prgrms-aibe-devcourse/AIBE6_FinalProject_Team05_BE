package com.pokade.domain.trade.controller;

import com.pokade.domain.trade.dto.TradeCreateRequest;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.response.ApiResponse;
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

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PostMapping
    public ApiResponse<TradeResponse> createTrade(
            @AuthenticationPrincipal Long buyerId,
            @Valid @RequestBody TradeCreateRequest request
    ) {
        return ApiResponse.ok("구매 요청이 접수되었습니다.", tradeService.createTrade(buyerId, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<TradeResponse> getTrade(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(tradeService.getTrade(userId, id));
    }

    @PatchMapping("/{id}/ship")
    public ApiResponse<TradeResponse> shipTrade(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok("발송 처리되었습니다.", tradeService.shipTrade(sellerId, id));
    }

    @PatchMapping("/{id}/confirm")
    public ApiResponse<TradeResponse> confirmTrade(
            @AuthenticationPrincipal Long buyerId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok("구매가 확정되었습니다.", tradeService.confirmTrade(buyerId, id));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<TradeResponse> cancelTrade(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok("거래가 취소되었습니다.", tradeService.cancelTrade(userId, id));
    }
}
