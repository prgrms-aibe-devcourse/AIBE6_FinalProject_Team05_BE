package com.pokade.domain.listing.dto;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingGrade;
import com.pokade.domain.listing.entity.ListingStatus;

import java.time.LocalDateTime;

// 마이페이지 "입찰" 섹션(판매 등록 탭)에서 항목을 클릭했을 때 보여주는 주문서 상세 - ListingSummaryResponse
// (목록용, 정산계좌·반송주소 없음)와 달리 등록 시점에 입력한 정산계좌/반송주소까지 포함한다.
public record MyListingResponse(
        Long id,
        Long cardId,
        String cardName,
        String cardNameKo,
        String cardImageUrl,
        Long variantId,
        Integer price,
        ListingGrade grade,
        ListingStatus status,
        String settlementBankName,
        String settlementAccountNumber,
        String settlementAccountHolder,
        String returnRecipientName,
        String returnRecipientPhone,
        String returnAddress,
        LocalDateTime createdAt
) {

    public static MyListingResponse of(Listing listing, Card card, String cardNameKo) {
        return new MyListingResponse(
                listing.getId(),
                listing.getCardId(),
                card == null ? null : card.getName(),
                cardNameKo,
                card == null ? null : card.getImageSmall(),
                listing.getVariantId(),
                listing.getPrice(),
                listing.getGrade(),
                listing.getStatus(),
                listing.getSettlementBankName(),
                listing.getSettlementAccountNumber(),
                listing.getSettlementAccountHolder(),
                listing.getReturnRecipientName(),
                listing.getReturnRecipientPhone(),
                listing.getReturnAddress(),
                listing.getCreatedAt()
        );
    }
}
