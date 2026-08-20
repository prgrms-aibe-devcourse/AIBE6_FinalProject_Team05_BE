package com.pokade.domain.point.client.dto;

// 토스페이먼츠 결제취소 API 응답 중 실제로 쓰는 필드만 매핑한다 - 취소가 최종 반영(CANCELED)됐는지만 확인한다.
public record TossCancelResponse(String paymentKey, String status) {

    private static final String STATUS_CANCELED = "CANCELED";

    public boolean isCanceled() {
        return STATUS_CANCELED.equals(status);
    }
}
