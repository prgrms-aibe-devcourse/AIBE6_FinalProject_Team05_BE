package com.pokade.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.ai.dto.GradeRequest;
import com.pokade.domain.ai.dto.GradeResponse;
import com.pokade.domain.ai.dto.VisionResult;
import com.pokade.domain.ai.entity.*;
import com.pokade.domain.ai.repository.GradeResultImageRepository;
import com.pokade.domain.ai.repository.GradeResultRepository;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.point.service.PointService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import com.pokade.global.web.PageableValidator;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiGradeService {

    private static final int FREE_LIMIT = 3;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int VISION_MAX_ATTEMPTS = 3;

    // OpenAI Vision이 실제로 지원하는 이미지 포맷 (ImageIO는 디코딩되지만 Vision은 거부하는 bmp/tiff 등을 사전 차단)
    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp");

    // 매 요청마다 new로 생성하지 않도록 공유 인스턴스 사용 (ObjectMapper는 thread-safe)
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int GRADE_COST = 100;

    private final ChatClient chatClient;
    private final S3FileStorage s3FileStorage;
    private final ImageQualityChecker imageQualityChecker;
    private final GradeResultRepository gradeResultRepository;
    private final GradeResultImageRepository gradeResultImageRepository;
    private final CardRepository cardRepository;
    private final PointService pointService;
    private final MeterRegistry meterRegistry;

    @Value("${pokade.ai.grade.model}")
    private String gradeModel;

    // Micrometer 메트릭
    // ai.grade.result{status, free} — 결과별 카운터 (Grafana: 성공률, 품질실패율, 유/무료 비율)
    private final Counter successFreeCounter;
    private final Counter successPaidCounter;
    private final Counter qualityFailCounter;
    // ai.grade.local_fail — 로컬 품질 검사에서 Vision 호출 없이 걸러진 횟수 (토큰 절약량 추적)
    private final Counter localQualityFailCounter;
    // ai.grade.vision.duration — Vision API 호출 시간 (Grafana: p95 레이턴시, 이상 탐지)
    private final Timer visionTimer;
    // ai.grade.vision.retries — Vision API 재시도 횟수 (Grafana: Vision 불안정 탐지용 경보 기준)
    private final Counter visionRetryCounter;

    public AiGradeService(ChatClient chatClient, S3FileStorage s3FileStorage,
                          ImageQualityChecker imageQualityChecker,
                          GradeResultRepository gradeResultRepository,
                          GradeResultImageRepository gradeResultImageRepository,
                          CardRepository cardRepository,
                          PointService pointService,
                          MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.s3FileStorage = s3FileStorage;
        this.imageQualityChecker = imageQualityChecker;
        this.gradeResultRepository = gradeResultRepository;
        this.gradeResultImageRepository = gradeResultImageRepository;
        this.cardRepository = cardRepository;
        this.pointService = pointService;
        this.meterRegistry = meterRegistry;

        this.successFreeCounter  = Counter.builder("ai.grade.result")
                .tag("status", "SUCCESS").tag("free", "true")
                .description("AI 등급 진단 성공(무료)")
                .register(meterRegistry);
        this.successPaidCounter  = Counter.builder("ai.grade.result")
                .tag("status", "SUCCESS").tag("free", "false")
                .description("AI 등급 진단 성공(유료)")
                .register(meterRegistry);
        this.qualityFailCounter  = Counter.builder("ai.grade.result")
                .tag("status", "QUALITY_FAIL").tag("free", "true")
                .description("AI 등급 진단 품질 실패")
                .register(meterRegistry);
        this.localQualityFailCounter = Counter.builder("ai.grade.local_fail")
                .description("로컬 품질 검사 실패로 Vision API 호출 생략된 횟수")
                .register(meterRegistry);
        this.visionTimer = Timer.builder("ai.grade.vision.duration")
                .description("OpenAI Vision API 호출 시간")
                .register(meterRegistry);
        this.visionRetryCounter = Counter.builder("ai.grade.vision.retries")
                .description("Vision API 재시도 횟수 — 높으면 Vision 불안정 신호")
                .register(meterRegistry);
    }

    // S3 업로드·Vision API 호출은 느린 외부 I/O라 DB 트랜잭션 밖에서 실행 — 커넥션을 오래 점유하지 않도록
    // DB 쓰기(재업로드 마킹·결과 저장)만 필요한 지점에서 별도로 처리한다.
    @Timed(value = "ai.grade.duration", description = "AI 등급 진단 전체 처리 시간")
    public GradeResponse grade(Long userId, GradeRequest request) {
        validateImageFormats(request);

        // ── 재업로드 요청 검증 ───────────────────────────────────────────────
        GradeResult originalResult = null;
        boolean isFreeRetry = false;
        if (request.retryOfId() != null) {
            boolean retryable = gradeResultRepository.existsRetryableResult(request.retryOfId(), userId, GradeStatus.QUALITY_FAIL);
            if (retryable) {
                originalResult = gradeResultRepository.findById(request.retryOfId()).orElseThrow();
                isFreeRetry = true;
            }
            // retryable하지 않으면 새 요청으로 처리 (유료 가능)
        }

        // ── 무료/유료 판단 ───────────────────────────────────────────────────
        long successCount = gradeResultRepository.countByUserIdAndStatus(userId, GradeStatus.SUCCESS);
        boolean isFree = isFreeRetry || successCount < FREE_LIMIT;

        // 유료 요청 사전 잔액 확인 (비관적 락 없이 빠른 실패 — Vision API 호출 전에 잔액 부족을 먼저 잡는다)
        // 실제 차감은 grade_result 저장 후 PointService.useForGrade()가 락을 잡고 재검증한다.
        if (!isFree) {
            pointService.verifyBalance(userId, GRADE_COST);
        }

        // ── S3 이미지 업로드 — Java 21 가상 스레드로 6개 파일 병렬 업로드 ─────
        // (imageKeys는 S3 key — 버킷이 프라이빗이라 조회 시 presigned URL로 변환 필요)
        Map<PhotoType, String> imageKeys = uploadImages(request);

        // ── Vision API 호출 → 등급 산출 (최대 3회 재시도) ──────────────────────
        VisionResult visionResult = evaluateQuality(request);

        // ── 결과 저장 ────────────────────────────────────────────────────────
        // Vision API 성공 확인 후 재업로드 마킹 — 이전에 하면 Vision 실패 시 기회 소멸 버그 발생
        if (originalResult != null) {
            originalResult.markRetryUsed();
            gradeResultRepository.save(originalResult);
        }

        GradeResult gradeResult = buildGradeResult(
                userId, visionResult, isFree,
                isFreeRetry ? request.retryOfId() : null);
        gradeResultRepository.save(gradeResult);

        // 이미지 key 연결 저장 — saveAll로 배치 INSERT
        List<GradeResultImage> images = new ArrayList<>();
        imageKeys.forEach((type, key) -> images.add(
                GradeResultImage.builder()
                        .gradeResultId(gradeResult.getId())
                        .photoType(type)
                        .imageUrl(key)
                        .build()));
        gradeResultImageRepository.saveAll(images);

        // ── 포인트 차감 (유료 요청) ──────────────────────────────────────────
        // grade_result.id를 relatedGradeResultId로 기록하므로 반드시 저장 후 호출
        Integer remainingPoints = null;
        if (!isFree && gradeResult.getStatus() == GradeStatus.SUCCESS) {
            remainingPoints = pointService.useForGrade(userId, GRADE_COST, gradeResult.getId());
        }

        // ── 메트릭 기록 ─────────────────────────────────────────────────────
        recordResultMetric(visionResult, isFree);

        // ── presigned URL 생성 후 응답 ───────────────────────────────────────
        Map<String, String> imageUrls = toPresignedUrls(imageKeys);
        return GradeResponse.from(gradeResult, resolveCard(gradeResult), imageUrls, remainingPoints);
    }

    private void recordResultMetric(VisionResult visionResult, boolean isFree) {
        if (visionResult.qualityIssue()) {
            qualityFailCounter.increment();
        } else if (isFree) {
            successFreeCounter.increment();
        } else {
            successPaidCounter.increment();
        }
    }

    public GradeResponse getGradeResult(Long userId, Long resultId) {
        GradeResult gradeResult = gradeResultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADE_RESULT_NOT_FOUND));

        if (!gradeResult.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        List<GradeResultImage> images = gradeResultImageRepository.findByGradeResultId(resultId);
        Map<String, String> imageUrls = images.stream()
                .collect(Collectors.toMap(
                        img -> img.getPhotoType().name(),
                        img -> s3FileStorage.generatePresignedUrl(img.getImageUrl())));

        return GradeResponse.from(gradeResult, resolveCard(gradeResult), imageUrls, null);
    }

    // vision_card_id(externalId)가 자체 DB에 없거나(신규 세트 동기화 지연 등) 아예 인식 실패면 null —
    // FE는 cardId=null을 "도감 등록 불가"로 취급한다(FR-AI-04).
    private Card resolveCard(GradeResult gradeResult) {
        return gradeResult.getVisionCardId() != null
                ? cardRepository.findByExternalId(gradeResult.getVisionCardId()).orElse(null)
                : null;
    }

    public Page<GradeResponse> getGradeHistory(Long userId, Pageable pageable) {
        PageableValidator.validatePageSize(pageable, MAX_PAGE_SIZE);
        Page<GradeResult> results = gradeResultRepository.findByUserId(userId, pageable);

        // 페이지 안에서 같은 카드가 여러 번 나올 수 있어 externalId 기준으로 배치 조회(N+1 방지).
        List<String> externalIds = results.getContent().stream()
                .map(GradeResult::getVisionCardId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Card> cardByExternalId = externalIds.isEmpty()
                ? Map.of()
                : cardRepository.findByExternalIdIn(externalIds).stream()
                        .collect(Collectors.toMap(Card::getExternalId, c -> c));

        // 이미지 URL·remainingPoints는 목록에서 제공하지 않음
        return results.map(r -> GradeResponse.from(r,
                r.getVisionCardId() != null ? cardByExternalId.get(r.getVisionCardId()) : null,
                null, null));
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

    // Java 21 가상 스레드로 6개 파일을 병렬 업로드 — 직렬 대비 약 1/6 시간 단축
    private Map<PhotoType, String> uploadImages(GradeRequest request) {
        List<PhotoType> types = List.of(
                PhotoType.FRONT, PhotoType.BACK,
                PhotoType.CORNER_TL, PhotoType.CORNER_TR,
                PhotoType.CORNER_BL, PhotoType.CORNER_BR);
        List<MultipartFile> files = List.of(
                request.front(), request.back(),
                request.cornerTl(), request.cornerTr(),
                request.cornerBl(), request.cornerBr());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = files.stream()
                    .map(file -> CompletableFuture.supplyAsync(
                            () -> s3FileStorage.upload(file, "ai-grade"), executor))
                    .toList();

            Map<PhotoType, String> keys = new LinkedHashMap<>();
            for (int i = 0; i < types.size(); i++) {
                keys.put(types.get(i), futures.get(i).join());
            }
            return keys;
        }
    }

    private Map<String, String> toPresignedUrls(Map<PhotoType, String> imageKeys) {
        return imageKeys.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> s3FileStorage.generatePresignedUrl(e.getValue())));
    }

    private VisionResult evaluateQuality(GradeRequest request) {
        Optional<String> localFailReason = imageQualityChecker.checkAll(request);
        if (localFailReason.isPresent()) {
            log.info("로컬 이미지 품질 사전 검사 실패로 Vision 호출 생략(토큰 절약): {}", localFailReason.get());
            localQualityFailCounter.increment();
            return VisionResult.localQualityFail(localFailReason.get());
        }

        // Vision API 일시 장애 대비 최대 3회 재시도 (새 의존성 없이 단순 루프)
        for (int attempt = 1; ; attempt++) {
            try {
                return callVisionApi(request);
            } catch (AiServiceUnavailableException e) {
                if (attempt >= VISION_MAX_ATTEMPTS) throw e;
                visionRetryCounter.increment();
                log.warn("Vision API 호출 실패, 재시도 ({}/{})...", attempt, VISION_MAX_ATTEMPTS);
            }
        }
    }

    private VisionResult callVisionApi(GradeRequest request) {
        List<MultipartFile> files = List.of(
                request.front(), request.back(),
                request.cornerTl(), request.cornerTr(),
                request.cornerBl(), request.cornerBr());

        try {
            return visionTimer.recordCallable(() -> {
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

                return OBJECT_MAPPER.readValue(extractJson(response), VisionResult.class);
            });

        } catch (AiServiceUnavailableException e) {
            throw e;
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
                    // 재업로드의 재업로드는 허용하지 않음 — 무한 무료 체인 방지
                    .retryAllowed(retryOfId == null)
                    .retryOfId(retryOfId)
                    .build();
        }

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
                .pointUsed(isFree ? 0 : GRADE_COST)
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
