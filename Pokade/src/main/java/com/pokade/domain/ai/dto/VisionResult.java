package com.pokade.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * OpenAI Vision API 응답을 역직렬화하는 내부 DTO
 */
public record VisionResult(
        String grade,

        @JsonProperty("centering_score")
        BigDecimal centeringScore,

        @JsonProperty("edge_score")
        BigDecimal edgeScore,

        @JsonProperty("surface_score")
        BigDecimal surfaceScore,

        @JsonProperty("corner_score")
        BigDecimal cornerScore,

        @JsonProperty("overall_confidence")
        BigDecimal overallConfidence,

        @JsonProperty("quality_issue")
        boolean qualityIssue,

        @JsonProperty("quality_issue_reason")
        String qualityIssueReason,

        @JsonProperty("card_external_id")
        String cardExternalId,

        @JsonProperty("card_confidence")
        BigDecimal cardConfidence
) {}
