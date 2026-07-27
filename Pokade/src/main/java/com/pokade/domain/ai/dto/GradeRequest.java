package com.pokade.domain.ai.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * POST /api/ai/grade 요청 파라미터
 * multipart/form-data 형식으로 수신
 */
public record GradeRequest(
        MultipartFile front,
        MultipartFile back,
        MultipartFile cornerTl,
        MultipartFile cornerTr,
        MultipartFile cornerBl,
        MultipartFile cornerBr,
        Long retryOfId    // 무료 재업로드 시 원본 grade_result_id (선택)
) {}
