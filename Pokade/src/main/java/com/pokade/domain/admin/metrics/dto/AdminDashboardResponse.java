package com.pokade.domain.admin.metrics.dto;

import java.util.List;

public record AdminDashboardResponse(List<AdminMetricCardResponse> cards, List<AdminMetricSeriesResponse> series) {
}
