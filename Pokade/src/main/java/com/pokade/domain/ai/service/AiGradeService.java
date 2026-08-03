package com.pokade.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.ai.dto.GradeRequest;
import com.pokade.domain.ai.dto.GradeResponse;
import com.pokade.domain.ai.dto.VisionResult;
import com.pokade.domain.ai.entity.*;
import com.pokade.domain.ai.repository.GradeResultImageRepository;
import com.pokade.domain.ai.repository.GradeResultRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGradeService {

    private static final int FREE_LIMIT = 3;
    private static final int MAX_PAGE_SIZE = 100;

    // OpenAI Vision이 실제로 지원하는 이미지 포맷 (ImageIO는 디코딩되지만 Vision은 거부하는 bmp/tiff 등을 사전 차단)
    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp");

    // TODO: User 파트 개발 완료 후 포인트 차감 연동 예정
    // private static final int GRADE_COST = 100;

    private final ChatClient chatClient;
    private final S3UploadService s3UploadService;
    private final ImageQualityChecker imageQualityChecker;
    private final GradeResultRepository gradeResultRepository;
    private final GradeResultImageRepository gradeResultImageRepository;

    // TODO: User 파트 개발 완료 후 아래 Repository 주입 및 포인트 차감 로직 연동 예정
    // private final UserRepository userRepository;
    // private final PointTransactionRepository pointTransactionRepository;

    @Value("${pokade.ai.grade.model}")
    private String gradeModel;

    // S3 업로드·Vision API 호출은 느린 외부 I/O라 DB 트랜잭션 밖에서 실행 — 커넥션을 오래 점유하지 않도록
    // DB 쓰기(재업로드 마킹·결과 저장)만 필요한 지점에서 별도로 처리한다.
    public GradeResponse grade(Long userId, GradeRequest request) {
        validateImageFormats(request);

        // ── 재업로드 요청 검증 ───────────────────────────────────────────────
        GradeResult originalResult = null;
        boolean isFreeRetry = false;
        if (request.retryOfId() != null) {
            boolean retryable = gradeResultRepository.existsRetryableResult(request.retryOfId(), userId);
            if (retryable) {
                originalResult = gradeResultRepository.findById(request.retryOfId()).orElseThrow();
                originalResult.markRetryUsed(); // retry_used = true (재사용 방지)
                // @Transactional 밖이라 영속성 컨텍스트가 없음 — 변경사항이 자동 flush되지 않으므로 명시적으로 save
                gradeResultRepository.save(originalResult);
                isFreeRetry = true;
            }
            // retryable하지 않으면 새 요청으로 처리 (유료 가능)
        }

        // ── 무료/유료 판단 ───────────────────────────────────────────────────
        long successCount = gradeResultRepository.countByUserIdAndStatus(userId, GradeStatus.SUCCESS);
        boolean isFree = isFreeRetry || successCount < FREE_LIMIT;

        // TODO: User 파트 개발 완료 후 포인트 차감 연동 예정
        // 유료(isFree=false)인 경우 users.point_balance에서 GRADE_COST(100) 차감 후
        // point_transactions에 type=USE 이력 저장 필요.
        // 동시 요청 방지를 위해 UserRepository.findByIdWithLock(userId) 사용할 것.
        // if (!isFree) {
        //     User user = userRepository.findByIdWithLock(userId).orElseThrow();
        //     user.deductPoints(GRADE_COST);
        //     pointTransactionRepository.save(PointTransaction.builder()
        //             .userId(userId)
        //             .type("USE")
        //             .amount(GRADE_COST)
        //             .balanceAfter(user.getPointBalance())
        //             .relatedGradeResultId(savedResult.getId())
        //             .build());
        // }

        // ── S3 이미지 업로드 (imageKeys는 S3 key — 버킷이 프라이빗이라 조회 시 presigned URL로 변환 필요) ──
        Map<PhotoType, String> imageKeys = uploadImages(request);

        // ── Vision API 호출 → 등급 산출 ──────────────────────────────────────
        VisionResult visionResult = evaluateQuality(request);

        // ── 결과 저장 ────────────────────────────────────────────────────────
        GradeResult gradeResult = buildGradeResult(
                userId, visionResult, isFree,
                isFreeRetry ? request.retryOfId() : null);
        gradeResultRepository.save(gradeResult);

        // 이미지 key 연결 저장 (imageUrl 컬럼에는 S3 key를 저장 — 프라이빗 버킷이라 고정 URL이 아님)
        imageKeys.forEach((type, key) ->
                gradeResultImageRepository.save(GradeResultImage.builder()
                        .gradeResultId(gradeResult.getId())
                        .photoType(type)
                        .imageUrl(key)
                        .build()));

        return GradeResponse.from(gradeResult);
    }

    public GradeResponse getGradeResult(Long userId, Long resultId) {
        GradeResult gradeResult = gradeResultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADE_RESULT_NOT_FOUND));

        if (!gradeResult.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return GradeResponse.from(gradeResult);
    }

    public Page<GradeResponse> getGradeHistory(Long userId, Pageable pageable) {
        validatePageSize(pageable);
        return gradeResultRepository.findByUserId(userId, pageable)
                .map(GradeResponse::from);
    }

    private void validatePageSize(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size는 최대 " + MAX_PAGE_SIZE + "까지 요청할 수 있습니다.");
        }
    }

    private void validateImageFormats(GradeRequest request) {
        List<MultipartFile> files = List.of(
                request.front(), request.back(),
                request.cornerTl(), request.cornerTr(),
                request.cornerBl(), request.cornerBr());

        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new IllegalArgumentException(
                        "지원하지 않는 이미지 형식입니다(png/jpeg/gif/webp만 가능): " + file.getOriginalFilename());
            }
        }
    }

    private Map<PhotoType, String> uploadImages(GradeRequest request) {
        Map<PhotoType, MultipartFile> files = new LinkedHashMap<>();
        files.put(PhotoType.FRONT,     request.front());
        files.put(PhotoType.BACK,      request.back());
        files.put(PhotoType.CORNER_TL, request.cornerTl());
        files.put(PhotoType.CORNER_TR, request.cornerTr());
        files.put(PhotoType.CORNER_BL, request.cornerBl());
        files.put(PhotoType.CORNER_BR, request.cornerBr());

        Map<PhotoType, String> urls = new LinkedHashMap<>();
        files.forEach((type, file) ->
                urls.put(type, s3UploadService.upload(file, "ai-grade")));
        return urls;
    }

    private VisionResult evaluateQuality(GradeRequest request) {
        Optional<String> localFailReason = imageQualityChecker.checkAll(request);
        if (localFailReason.isPresent()) {
            log.info("로컬 이미지 품질 사전 검사 실패로 Vision 호출 생략(토큰 절약): {}", localFailReason.get());
            return VisionResult.localQualityFail(localFailReason.get());
        }
        return callVisionApi(request);
    }

    private VisionResult callVisionApi(GradeRequest request) {
        List<MultipartFile> files = List.of(
                request.front(), request.back(),
                request.cornerTl(), request.cornerTr(),
                request.cornerBl(), request.cornerBr());

        try {
            String response = chatClient.prompt()
                    .user(u -> {
                        u.text(buildPrompt());
                        files.forEach(file -> u.media(
                                mimeTypeOf(file),
                                toResource(file)));
                    })
                    .options(OpenAiChatOptions.builder().model(gradeModel))
                    .call()
                    .content();

            return new ObjectMapper().readValue(extractJson(response), VisionResult.class);

        } catch (Exception e) {
            log.error("Vision API 호출 실패", e);
            throw new AiServiceUnavailableException("AI 등급 진단 서비스에 일시적인 오류가 발생했습니다.");
        }
    }

    // 저해상도 축소 시 표면 스크래치·모서리 화이트닝 등 미세 결함이 뭉개져 판단 정확도가 떨어질 수 있어 원본 화질 그대로 전송
    private Resource toResource(MultipartFile file) {
        try {
            return new InputStreamResource(file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("이미지를 읽을 수 없습니다: " + file.getOriginalFilename(), e);
        }
    }

    private MimeType mimeTypeOf(MultipartFile file) {
        try {
            return MimeTypeUtils.parseMimeType(file.getContentType());
        } catch (Exception e) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
    }

    private String buildPrompt() {
        return """
                당신은 포켓몬 트레이딩 카드 등급 전문가입니다.
                제공된 카드 이미지 6장(앞면 1장, 뒷면 1장, 모서리 클로즈업 4장)을 분석하여 등급을 산출하세요.

                등급 기준:
                - S (PSA 9-10 상당): 민트~민트 상태. 결함 없음. 날카로운 모서리, 깨끗한 엣지, 중앙 정렬.
                - A (PSA 7-8 상당): 엑셀런트~니어민트. 경미한 결함 허용. 약간의 모서리/엣지 마모.
                - B (PSA 5-6 상당): 굿~엑셀런트. 보통 수준 마모. 눈에 띄는 결함 있으나 감상 가능.

                사진 품질이 너무 낮아 평가 불가능한 경우(흐림, 어둠, 잘못된 각도 등)는 quality_issue를 true로 설정하세요.

                반드시 아래 JSON 형식으로만 응답하세요 (다른 텍스트 없이):
                {
                  "grade": "S" 또는 "A" 또는 "B" 또는 null,
                  "centering_score": 0.00~10.00,
                  "edge_score": 0.00~10.00,
                  "surface_score": 0.00~10.00,
                  "corner_score": 0.00~10.00,
                  "overall_confidence": 0.00~100.00,
                  "quality_issue": true 또는 false,
                  "quality_issue_reason": "사유 또는 null",
                  "card_external_id": "Scrydex 카드 ID(예: base1-4) 또는 null",
                  "card_confidence": 0.00~100.00
                }
                """;
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1) {
            throw new RuntimeException("Vision 응답에서 JSON을 추출할 수 없습니다: " + raw);
        }
        return raw.substring(start, end + 1);
    }

    private GradeResult buildGradeResult(Long userId, VisionResult vision,
                                         boolean isFree, Long retryOfId) {
        if (vision.qualityIssue()) {
            return GradeResult.builder()
                    .userId(userId)
                    .status(GradeStatus.QUALITY_FAIL)
                    .isFree(true)        // 품질 실패는 과금하지 않음
                    .pointUsed(0)
                    .retryAllowed(true)  // 무료 재업로드 1회 부여
                    .retryOfId(retryOfId)
                    .build();
        }

        // TODO: User 파트 개발 완료 후 isFree=false 시 pointUsed=GRADE_COST 로 변경 예정
        return GradeResult.builder()
                .userId(userId)
                .status(GradeStatus.SUCCESS)
                .grade(vision.grade())
                .centeringScore(vision.centeringScore())
                .edgeScore(vision.edgeScore())
                .surfaceScore(vision.surfaceScore())
                .cornerScore(vision.cornerScore())
                .confidence(vision.overallConfidence())
                .visionCardId(vision.cardExternalId())
                .visionConfidence(vision.cardConfidence())
                .isFree(isFree)
                .pointUsed(0) // TODO: User 파트 완료 후 !isFree 시 GRADE_COST 로 교체
                .retryAllowed(false)
                .retryOfId(retryOfId)
                .build();
    }

    public static class AiServiceUnavailableException extends RuntimeException {
        public AiServiceUnavailableException(String message) {
            super(message);
        }
    }
}
