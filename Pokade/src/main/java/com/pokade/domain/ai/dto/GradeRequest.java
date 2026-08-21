package com.pokade.domain.ai.dto;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
) {
    /** 6개 이미지를 PhotoType 순서대로 반환 — 반복 열거 중복 제거용 */
    public List<MultipartFile> files() {
        return List.of(front, back, cornerTl, cornerTr, cornerBl, cornerBr);
    }
}
