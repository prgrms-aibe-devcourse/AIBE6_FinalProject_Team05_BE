package com.pokade.domain.point.dto.response;

// 프론트가 토스페이먼츠 결제위젯 SDK를 띄울 때 필요한 값. clientKey는 공개해도 되는 값이라 프론트가
// 자체 환경변수로 따로 들고 있고(백엔드가 내려줄 필요 없음), 여기서는 orderId/amount만 전달한다.
public record PointChargeReadyResponse(String orderId, Integer amount) {
}
