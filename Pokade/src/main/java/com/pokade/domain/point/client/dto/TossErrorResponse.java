package com.pokade.domain.point.client.dto;

// 토스페이먼츠가 4xx/5xx에 내려주는 에러 응답 형식.
public record TossErrorResponse(String code, String message) {
}
