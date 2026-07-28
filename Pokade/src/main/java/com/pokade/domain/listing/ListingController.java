package com.pokade.domain.listing;

import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import com.pokade.domain.listing.dto.ListingSummaryResponse;
import com.pokade.domain.listing.dto.OrderbookEntryResponse;
import com.pokade.global.response.ApiResponse;
import com.pokade.domain.listing.dto.ListingUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            // TODO: 인증 파트 완성되면 SecurityContext에서 sellerId 추출하는 방식으로 교체
            @RequestHeader("X-USER-ID") Long sellerId,
            @Valid @RequestBody ListingCreateRequest request
    ) {
        ListingResponse response = listingService.createListing(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ListingSummaryResponse>> getActiveListings(@RequestParam Long cardId) {
        return ResponseEntity.ok(listingService.getActiveListings(cardId));
    }

    @GetMapping("/{cardId}/orderbook")
    public ApiResponse<List<OrderbookEntryResponse>> getOrderbook(
            @PathVariable Long cardId,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) ListingGrade grade
    ) {
        return ApiResponse.ok(listingService.getOrderbook(cardId, variantId, grade));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ListingSummaryResponse>> getMyListings(
            // TODO: 인증 파트 완성되면 SecurityContext에서 sellerId 추출하는 방식으로 교체
            @RequestHeader("X-USER-ID") Long sellerId,
            @RequestParam(required = false) ListingStatus status
    ) {
        return ResponseEntity.ok(listingService.getMyListings(sellerId, status));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> updateListing(
            // TODO: 인증 파트 완성되면 SecurityContext에서 sellerId 추출하는 방식으로 교체
            @RequestHeader("X-USER-ID") Long sellerId,
            @PathVariable Long id,
            @Valid @RequestBody ListingUpdateRequest request
    ) {
        return ResponseEntity.ok(listingService.updatePrice(sellerId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteListing(
            // TODO: 인증 파트 완성되면 SecurityContext에서 sellerId 추출하는 방식으로 교체
            @RequestHeader("X-USER-ID") Long sellerId,
            @PathVariable Long id
    ) {
        listingService.deleteListing(sellerId, id);
        return ResponseEntity.noContent().build();
    }
}
