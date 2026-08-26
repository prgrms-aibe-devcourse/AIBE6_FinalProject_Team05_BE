package com.pokade.domain.price.controller;

import com.pokade.domain.price.dto.BuyOfferFulfillRequest;
import com.pokade.domain.price.dto.BuyOfferPaymentConfirmRequest;
import com.pokade.domain.price.dto.BuyOfferReadyRequest;
import com.pokade.domain.price.dto.BuyOfferReadyResponse;
import com.pokade.domain.price.dto.BuyOfferResponse;
import com.pokade.domain.price.dto.MyBuyOfferResponse;
import com.pokade.domain.price.dto.BuyOfferRecipientUpdateRequest;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 조회(GET /api/prices/{cardId}/buy-offers)는 카드 단위 리소스라 PriceController에 있지만,
// 등록은 ListingController의 POST /api/listings(카드 단위 경로 없이 바디로 cardId를 받음)와
// 대칭을 맞추기 위해 별도 컨트롤러/경로로 둔다. 서비스 로직은 여전히 PriceService에 둔다
// (BuyOffer 조회/생성 로직을 같은 곳에 모아두는 편이 지금 규모에서 더 단순함).
// 구매입찰은 등록 시점에 바로 토스 에스크로 결제를 진행하므로 TradeController의 ready/confirm-payment
// 2단계 패턴을 그대로 미러링한다.
@Tag(name = "구매입찰", description = "구매입찰 등록(결제 준비/승인)·즉시판매 체결·내 입찰 조회 및 수정 API")
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

    @Operation(
            summary = "구매입찰 즉시판매(체결)",
            description = "이미 결제가 완료된 구매입찰에 판매자가 자신의 카드를 즉시 매칭해 거래를 확정합니다."
    )
    @PostMapping("/{buyOfferId}/fulfill")
    public ApiResponse<TradeResponse> fulfill(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long buyOfferId,
            @Valid @RequestBody BuyOfferFulfillRequest request
    ) {
        return ApiResponse.ok("즉시판매가 체결되었습니다.", priceService.fulfillBuyOffer(buyOfferId, sellerId, request));
    }

    @Operation(
            summary = "내 구매입찰 목록 조회",
            description = "로그인한 사용자가 buyer로 등록한 구매입찰 목록을 상태별로 페이징 조회합니다."
    )
    @GetMapping("/me")
    public ApiResponse<Page<MyBuyOfferResponse>> getMyBuyOffers(
            @AuthenticationPrincipal Long buyerId,
            @Parameter(description = "구매입찰 상태 필터 (선택, ACTIVE/MATCHED/PARTIAL/EXPIRED/CANCELLED)")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(priceService.getMyBuyOffers(buyerId, status, pageable));
    }

    @Operation(
            summary = "내 구매입찰 주문서 상세 조회",
            description = "마이페이지 '입찰' 목록에서 항목을 클릭했을 때 보여주는 주문서 상세입니다. 본인 것이 아니면 403을 반환합니다."
    )
    @GetMapping("/{buyOfferId}")
    public ApiResponse<MyBuyOfferResponse> getMyBuyOffer(
            @AuthenticationPrincipal Long buyerId,
            @PathVariable Long buyOfferId
    ) {
        return ApiResponse.ok(priceService.getMyBuyOffer(buyOfferId, buyerId));
    }

    @Operation(
            summary = "구매입찰 결제 취소",
            description = "결제 완료(ACTIVE)된 구매입찰을 취소합니다. 토스 에스크로 결제취소 및/또는 포인트 환불을 처리합니다."
    )
    @DeleteMapping("/{buyOfferId}")
    public ApiResponse<MyBuyOfferResponse> cancel(
            @AuthenticationPrincipal Long buyerId,
            @PathVariable Long buyOfferId
    ) {
        return ApiResponse.ok("구매입찰이 취소되었습니다.", priceService.cancelBuyOffer(buyOfferId, buyerId));
    }

    @Operation(
            summary = "구매입찰 받는사람 정보 수정",
            description = "결제 완료된 구매입찰의 받는사람 정보를 수정합니다. ACTIVE 상태가 아니면(이미 체결/만료) 실패합니다."
    )
    @PatchMapping("/{buyOfferId}")
    public ApiResponse<MyBuyOfferResponse> updateRecipient(
            @AuthenticationPrincipal Long buyerId,
            @PathVariable Long buyOfferId,
            @Valid @RequestBody BuyOfferRecipientUpdateRequest request
    ) {
        return ApiResponse.ok(
                "받는사람 정보가 수정되었습니다.", priceService.updateBuyOfferRecipient(buyOfferId, buyerId, request));
    }
}
