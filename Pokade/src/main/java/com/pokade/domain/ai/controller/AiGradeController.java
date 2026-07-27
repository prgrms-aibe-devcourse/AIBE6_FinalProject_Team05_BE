package com.pokade.domain.ai.controller;

import com.pokade.domain.ai.dto.GradeRequest;
import com.pokade.domain.ai.dto.GradeResponse;
import com.pokade.domain.ai.service.AiGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

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

            Principal principal
    ) {
        // TODO: OAuth2 연동 완료 후 principal에서 실제 userId 추출
        Long userId = extractUserId(principal);

        GradeRequest request = new GradeRequest(front, back, cornerTl, cornerTr, cornerBl, cornerBr, retryOfId);
        GradeResponse response = aiGradeService.grade(userId, request);
        return ResponseEntity.ok(response);
    }

    private Long extractUserId(Principal principal) {
        // TODO: OAuth2 UserDetails에서 userId 추출로 교체
        if (principal == null) {
            throw new IllegalStateException("인증이 필요합니다.");
        }
        // 임시: principal.getName()이 userId인 경우 (개발 단계)
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("유효하지 않은 사용자 정보입니다.");
        }
    }
}
