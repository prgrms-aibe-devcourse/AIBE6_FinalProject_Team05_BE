package com.pokade.domain.listing.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;

import java.time.LocalDateTime;

public record ListingSummaryResponse(
        Long id,
        Long sellerId,
        Long cardId,
        String cardName,
        // 한글 매핑이 없으면 null(어설픈 오번역보다 안전하다는 CardNameKoResolver의 설계, watchlist.ts와
        // 동일). 표시할 땐 cardNameKo ?? cardName.
        String cardNameKo,
        String cardImageUrl,
        Integer price,
        ListingGrade grade,
        ListingStatus status,
        LocalDateTime createdAt,
        Long tradeId
) {

    public static ListingSummaryResponse of(Listing listing, Card card, String cardNameKo) {
        return of(listing, card, cardNameKo, null);
    }

    // tradeId: 이 매물에 연결된 거래 ID(있는 경우만) — "내 상품관리"에서 거래 진행 상황 화면으로 연결하기 위해 추가.
    public static ListingSummaryResponse of(Listing listing, Card card, String cardNameKo, Long tradeId) {
        return new ListingSummaryResponse(
                listing.getId(),
                listing.getSellerId(),
                listing.getCardId(),
                card == null ? null : card.getName(),
                cardNameKo,
                card == null ? null : card.getImageSmall(),
                listing.getPrice(),
                listing.getGrade(),
                listing.getStatus(),
                listing.getCreatedAt(),
                tradeId
        );
    }
}
