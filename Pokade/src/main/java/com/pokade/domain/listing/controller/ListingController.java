package com.pokade.domain.listing.controller;

import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import com.pokade.domain.listing.dto.ListingSummaryResponse;
import com.pokade.domain.listing.dto.OrderbookEntryResponse;
import com.pokade.global.response.ApiResponse;
import com.pokade.domain.listing.dto.ListingUpdateRequest;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "매물", description = "카드 매물 등록/조회/수정/삭제 API")
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @Operation(summary = "매물 등록", description = "판매할 카드 매물을 등록합니다.")
    @PostMapping
    public ApiResponse<ListingResponse> createListing(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody ListingCreateRequest request
    ) {
        return ApiResponse.ok("매물이 등록되었습니다.", listingService.createListing(sellerId, request));
    }

    @Operation(summary = "판매 중인 매물 목록 조회", description = "특정 카드의 판매 중(ACTIVE)인 매물 목록을 가격 오름차순으로 조회합니다.")
    @GetMapping
    public ApiResponse<List<ListingSummaryResponse>> getActiveListings(
            @Parameter(description = "카드 ID") @RequestParam Long cardId
    ) {
        return ApiResponse.ok(listingService.getActiveListings(cardId));
    }

    @Operation(summary = "호가창 조회", description = "특정 카드의 매물을 등급/변형별로 필터링하여 호가창 형태로 조회합니다.")
    @GetMapping("/{cardId}/orderbook")
    public ApiResponse<List<OrderbookEntryResponse>> getOrderbook(
            @Parameter(description = "카드 ID") @PathVariable Long cardId,
            @Parameter(description = "카드 변형 ID (선택)") @RequestParam(required = false) Long variantId,
            @Parameter(description = "매물 등급 필터 (선택)") @RequestParam(required = false) ListingGrade grade
    ) {
        return ApiResponse.ok(listingService.getOrderbook(cardId, variantId, grade));
    }

    @Operation(summary = "내 매물 목록 조회", description = "로그인한 사용자가 판매자로 등록한 매물 목록을 상태별로 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<List<ListingSummaryResponse>> getMyListings(
            @AuthenticationPrincipal Long sellerId,
            @Parameter(description = "매물 상태 필터 (선택)") @RequestParam(required = false) ListingStatus status
    ) {
        return ApiResponse.ok(listingService.getMyListings(sellerId, status));
    }

    @Operation(summary = "매물 가격 수정", description = "판매자 본인의 매물 가격을 수정합니다. 판매 중(ACTIVE) 상태가 아니면 실패합니다.")
    @PutMapping("/{id}")
    public ApiResponse<ListingResponse> updateListing(
            @AuthenticationPrincipal Long sellerId,
            @Parameter(description = "매물 ID") @PathVariable Long id,
            @Valid @RequestBody ListingUpdateRequest request
    ) {
        return ApiResponse.ok("매물 가격이 수정되었습니다.", listingService.updatePrice(sellerId, id, request));
    }

    @Operation(summary = "매물 삭제", description = "판매자 본인의 매물을 삭제(취소)합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteListing(
            @AuthenticationPrincipal Long sellerId,
            @Parameter(description = "매물 ID") @PathVariable Long id
    ) {
        listingService.deleteListing(sellerId, id);
        return ApiResponse.ok("매물이 삭제되었습니다.");
    }
}
