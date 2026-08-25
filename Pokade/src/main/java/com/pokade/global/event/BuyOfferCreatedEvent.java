package com.pokade.global.event;

import com.pokade.domain.listing.entity.ListingGrade;

// 구매입찰이 실제로 등록된(=결제 승인까지 끝난) 시점에 발행된다 - PriceService.confirmBuyOfferPurchase()가
// 유일한 발행 지점이다. readyBuyOffer()는 아직 BuyOffer 행이 없는 단계라 발행하지 않는다.
//
// ListingCreatedEvent와 나란히 두는 이유: 둘 다 "한 도메인에서 일어난 일을 다른 도메인이 알림으로 바꾸는"
// 같은 성격이고, 서로 참조하지 않는 두 도메인이 공유해야 해서 global에 둔다.
//
// ListingGrade(listing 도메인 enum)를 참조하지만 BuyOffer 엔티티가 이미 같은 타입을 쓰고 있어
// 새로 생기는 결합은 아니다. grade는 null일 수 있고 "등급 무관 입찰"을 뜻한다.
public record BuyOfferCreatedEvent(
        Long buyOfferId,
        Long cardId,
        Long variantId,
        ListingGrade grade,
        Long buyerId,
        Integer price
) {
}
