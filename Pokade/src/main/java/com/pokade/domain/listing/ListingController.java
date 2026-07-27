package com.pokade.domain.listing;

import com.pokade.domain.listing.dto.ListingCreateRequest;
import com.pokade.domain.listing.dto.ListingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
