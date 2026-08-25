package com.pokade.domain.listing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 정산계좌/반송주소 6개 필드는 전부 선택 - 생략(null)하면 기존 값을 그대로 둔다(부분 수정).
// "내 매물 관리"(/listings/me)의 가격만 바꾸는 빠른 수정과 "주문서" 화면의 전체 수정이 같은
// API를 쓰는데, 전자는 이 필드들을 아예 안 보내므로 필수로 만들면 그 플로우가 깨진다.
public record ListingUpdateRequest(
        @NotNull(message = "price는 필수입니다.")
        @Positive(message = "price는 0보다 커야 합니다.")
        Integer price,

        String settlementBankName,
        String settlementAccountNumber,
        String settlementAccountHolder,
        String returnRecipientName,
        String returnRecipientPhone,
        String returnAddress
) {
}
