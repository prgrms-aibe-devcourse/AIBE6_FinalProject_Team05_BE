package com.pokade.domain.point.client.dto;

// 토스페이먼츠 결제 승인 API 응답 중 우리가 실제로 쓰는 필드만 매핑한다 - 전체 Payment 객체는 필드가
// 훨씬 많지만(카드 정보, 영수증 URL 등) 지금은 결제가 최종 승인(DONE)됐는지와 실제 승인 금액만 필요하다.
public record TossConfirmResponse(String paymentKey, String orderId, String status, Long totalAmount) {

    private static final String STATUS_DONE = "DONE";

    public boolean isDone() {
        return STATUS_DONE.equals(status);
    }
}
