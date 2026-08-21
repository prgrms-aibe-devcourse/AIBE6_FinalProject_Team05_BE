package com.pokade.domain.price.controller;

import com.pokade.domain.price.dto.BuyOfferPaymentConfirmRequest;
import com.pokade.domain.price.dto.BuyOfferReadyRequest;
import com.pokade.domain.price.dto.BuyOfferReadyResponse;
import com.pokade.domain.price.dto.BuyOfferResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 조회(GET /api/prices/{cardId}/buy-offers)는 카드 단위 리소스라 PriceController에 있지만,
// 등록은 ListingController의 POST /api/listings(카드 단위 경로 없이 바디로 cardId를 받음)와
// 대칭을 맞추기 위해 별도 컨트롤러/경로로 둔다. 서비스 로직은 여전히 PriceService에 둔다
// (BuyOffer 조회/생성 로직을 같은 곳에 모아두는 편이 지금 규모에서 더 단순함).
// 구매입찰은 등록 시점에 바로 토스 에스크로 결제를 진행하므로 TradeController의 ready/confirm-payment
// 2단계 패턴을 그대로 미러링한다.
@RestController
@RequestMapping("/api/buy-offers")
@RequiredArgsConstructor
public class BuyOfferController {

    private final PriceService priceService;

    @Operation(
            summary = "구매입찰 결제 준비",
            description = "구매입찰 등록을 위한 토스페이먼츠 결제창을 띄우기 전, 주문을 PENDING으로 먼저 기록합니다."
    )
    @PostMapping("/ready")
    public ApiResponse<BuyOfferReadyResponse> ready(
            @AuthenticationPrincipal Long buyerId,
            @Valid @RequestBody BuyOfferReadyRequest request
    ) {
        return ApiResponse.ok(priceService.readyBuyOffer(buyerId, request));
    }

    @Operation(
            summary = "구매입찰 결제 승인",
            description = "토스페이먼츠 결제창 successUrl 리다이렉트 이후 호출합니다. 결제 승인 후 구매입찰을 생성합니다."
    )
    @PostMapping("/confirm-payment")
    public ApiResponse<BuyOfferResponse> confirmPurchase(
            @AuthenticationPrincipal Long buyerId,
            @Valid @RequestBody BuyOfferPaymentConfirmRequest request
    ) {
        BuyOfferResponse response = priceService.confirmBuyOfferPurchase(
                buyerId, request.paymentKey(), request.orderId(), request.amount());
        return ApiResponse.ok("구매입찰이 등록되었습니다.", response);
    }
}
