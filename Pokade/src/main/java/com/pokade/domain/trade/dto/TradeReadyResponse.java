package com.pokade.domain.trade.dto;

// 프론트가 토스페이먼츠 결제위젯 SDK를 띄울 때 필요한 값. clientKey는 프론트가 자체 환경변수로
// 들고 있어 여기서는 orderId/amount만 전달한다 (PointChargeReadyResponse와 동일한 관례).
public record TradeReadyResponse(String orderId, Integer amount) {
}
