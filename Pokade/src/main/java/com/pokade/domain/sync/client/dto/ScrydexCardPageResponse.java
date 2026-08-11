package com.pokade.domain.sync.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScrydexCardPageResponse(
        List<CardDto> data,
        Integer page,
        @JsonProperty("page_size") Integer pageSize,
        Integer count,
        @JsonProperty("total_count") Integer totalCount
) {
}
