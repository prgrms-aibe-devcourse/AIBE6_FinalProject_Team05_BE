package com.pokade.domain.admin.metrics.dto;

// 단일 값(스냅샷)으로 보여주는 지표 카드. value가 null이면 아직 데이터가 없다는 뜻(FE는 "데이터 없음"으로 표시).
// subLabel/subValue는 "총 방문자 수 (오늘 증가한 방문자 수)"처럼 보조 수치가 있는 카드에서만 채워진다.
public record AdminMetricCardResponse(String key, String label, Double value, String unit,
                                       String subLabel, Double subValue) {
}
