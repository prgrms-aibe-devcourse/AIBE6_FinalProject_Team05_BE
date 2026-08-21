package com.pokade.domain.listing.dto;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;

import java.time.LocalDateTime;

public record ListingSummaryResponse(
        Long id,
        Long sellerId,
        Long cardId,
        String cardName,
        Integer price,
        ListingGrade grade,
        ListingStatus status,
        LocalDateTime createdAt,
        Long tradeId
) {

    public static ListingSummaryResponse of(Listing listing, String cardName) {
        return of(listing, cardName, null);
    }

    // tradeId: 이 매물에 연결된 거래 ID(있는 경우만) — "내 상품관리"에서 거래 진행 상황 화면으로 연결하기 위해 추가.
    public static ListingSummaryResponse of(Listing listing, String cardName, Long tradeId) {
        return new ListingSummaryResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getCardId(),
                cardName,
                listing.getPrice(),
                listing.getGrade(),
                listing.getStatus(),
                listing.getCreatedAt(),
                tradeId
        );
    }
}
