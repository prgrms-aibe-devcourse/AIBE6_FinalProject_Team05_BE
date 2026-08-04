package com.pokade.domain.ai.controller;

import com.pokade.domain.ai.dto.GradeRequest;
import com.pokade.domain.ai.dto.GradeResponse;
import com.pokade.domain.ai.service.AiGradeService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "AI 등급 진단", description = "포켓몬 카드 AI 예비 등급 진단 API")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGradeController {

    private final AiGradeService aiGradeService;

    @Operation(
            summary = "카드 AI 등급 진단 요청",
            description = """
                    카드 사진 6장(앞면, 뒷면, 모서리 4장)을 업로드하면 AI가 S/A/B 등급을 예측합니다.

                    **과금 정책**
                    - 무료 3회: 정상 산출(SUCCESS) 건만 카운트
                    - 4회차~: 건당 100포인트 차감 (후결제)
                    - 사진 품질 실패 시: 무료 재업로드 1회 허용 (retryOfId 파라미터 사용)

                    **법적 고지**: 본 결과는 AI 기반 참고용 예비진단이며, 정식 카드 감정을 대체하지 않습니다.
                    """
    )
    @PostMapping(value = "/grade", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GradeResponse> grade(
            @Parameter(description = "카드 앞면 사진", required = true)
            @RequestPart MultipartFile front,

            @Parameter(description = "카드 뒷면 사진", required = true)
            @RequestPart MultipartFile back,

            @Parameter(description = "좌상단 모서리 클로즈업", required = true)
            @RequestPart("corner_tl") MultipartFile cornerTl,

            @Parameter(description = "우상단 모서리 클로즈업", required = true)
            @RequestPart("corner_tr") MultipartFile cornerTr,

            @Parameter(description = "좌하단 모서리 클로즈업", required = true)
            @RequestPart("corner_bl") MultipartFile cornerBl,

            @Parameter(description = "우하단 모서리 클로즈업", required = true)
            @RequestPart("corner_br") MultipartFile cornerBr,

            @Parameter(description = "무료 재업로드 시 원본 grade_result_id")
            @RequestParam(required = false) Long retryOfId,

            @AuthenticationPrincipal Long principalUserId
    ) {
        Long userId = requireUserId(principalUserId);

        GradeRequest request = new GradeRequest(front, back, cornerTl, cornerTr, cornerBl, cornerBr, retryOfId);
        GradeResponse response = aiGradeService.grade(userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "AI 등급 진단 결과 조회",
            description = "진단 요청(POST /api/ai/grade) 응답으로 받은 resultId로 상세 결과를 조회합니다. 본인이 요청한 결과만 조회 가능합니다."
    )
    @GetMapping("/grade/{resultId}")
    public ResponseEntity<GradeResponse> getGradeResult(
            @Parameter(description = "조회할 진단 결과 ID", required = true)
            @PathVariable Long resultId,

            @AuthenticationPrincipal Long principalUserId
    ) {
        Long userId = requireUserId(principalUserId);
        GradeResponse response = aiGradeService.getGradeResult(userId, resultId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "AI 등급 진단 이력 조회",
            description = "본인이 요청한 AI 등급 진단 이력을 최신순으로 페이징 조회합니다."
    )
    @GetMapping("/grade/history")
    public ResponseEntity<Page<GradeResponse>> getGradeHistory(
            @AuthenticationPrincipal Long principalUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Long userId = requireUserId(principalUserId);
        Page<GradeResponse> response = aiGradeService.getGradeHistory(userId, pageable);
        return ResponseEntity.ok(response);
    }

    private Long requireUserId(Long principalUserId) {
        if (principalUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principalUserId;
    }
}
