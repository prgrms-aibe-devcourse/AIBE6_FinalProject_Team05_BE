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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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

    // OpenAI Structured Outputs(response_format=json_schema, strict) 강제용 스키마.
    // 모델이 이 형태를 벗어난 값(예: grade에 "S+" 같은 임의 문자열, 점수 필드에 문자열)을 아예 생성하지
    // 못하게 API 레벨에서 막아준다 - 프롬프트로 "JSON만 답하라"고 부탁하는 것과 차원이 다르다.
    // strict 모드 제약: properties에 있는 필드는 전부 required에도 있어야 하고(선택적 필드는 type에
    // "null"을 추가해서 표현), 모든 object는 additionalProperties:false가 필요하다.
    private static final String VISION_RESPONSE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "grade": { "type": ["string", "null"], "enum": ["S", "A", "B", null] },
                "centering_score": { "type": ["number", "null"] },
                "edge_score": { "type": ["number", "null"] },
                "surface_score": { "type": ["number", "null"] },
                "corner_score": { "type": ["number", "null"] },
                "overall_confidence": { "type": ["number", "null"] },
                "quality_issue": { "type": "boolean" },
                "quality_issue_reason": { "type": ["string", "null"] },
                "card_external_id": { "type": ["string", "null"] },
                "card_confidence": { "type": ["number", "null"] }
              },
              "required": ["grade", "centering_score", "edge_score", "surface_score", "corner_score",
                           "overall_confidence", "quality_issue", "quality_issue_reason",
                           "card_external_id", "card_confidence"],
              "additionalProperties": false
            }
            """;

    private static final int GRADE_COST = 100;

    private final ChatClient chatClient;
    private final S3FileStorage s3FileStorage;
    private final ImageQualityChecker imageQualityChecker;
    private final GradeResultRepository gradeResultRepository;
    private final GradeResultImageRepository gradeResultImageRepository;
    private final CardRepository cardRepository;
    private final PointService pointService;

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
    // ai.grade.cache.hits — 동일 이미지 재요청으로 Vision 호출을 생략한 횟수 (비용 절감 + 등급 일관성 지표)
    private final Counter cacheHitCounter;

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
        this.cacheHitCounter = Counter.builder("ai.grade.cache.hits")
                .description("동일 이미지 해시로 Vision 호출을 생략하고 캐시된 결과를 반환한 횟수")
                .register(meterRegistry);
    }

    // S3 업로드·Vision API 호출은 느린 외부 I/O라 DB 트랜잭션 밖에서 실행 — 커넥션을 오래 점유하지 않도록
    // DB 쓰기(재업로드 마킹·결과 저장)만 필요한 지점에서 별도로 처리한다.
    @Timed(value = "ai.grade.duration", description = "AI 등급 진단 전체 처리 시간")
    public GradeResponse grade(Long userId, GradeRequest request) {
        validateImageFormats(request);

        // ── 동일 이미지 캐시 조회 ────────────────────────────────────────────
        // 같은 6장을 다시 보내면 Vision을 또 부르지 않고 이전 SUCCESS 결과를 그대로 돌려준다(무료).
        // 등급 비일관성(같은 카드인데 S/A 왔다갔다)을 원천 차단하는 게 우선이라는 팀 결정(B안).
        // retryOfId/포인트 로직보다 먼저 검사해서, 캐시로 끝날 요청이 무료 재시도 기회를 소모하거나
        // 잔액을 검증하는 일이 없게 한다.
        String imageHash = computeImageHash(request.files());
        Optional<GradeResult> cached = gradeResultRepository
                .findFirstByUserIdAndImageHashAndStatusOrderByCreatedAtDesc(userId, imageHash, GradeStatus.SUCCESS);
        if (cached.isPresent()) {
            cacheHitCounter.increment();
            return buildCachedResponse(cached.get());
        }

        // ── 재업로드 요청 검증 ───────────────────────────────────────────────
        // claimRetry: 조건 확인 + retryUsed=true 마킹을 단일 UPDATE로 원자 처리 (check-then-act 경쟁 방지).
        // Vision 호출 전에 클레임하므로 Vision 3회 실패 시 재시도 기회가 소멸되는 트레이드오프가 있으나,
        // 경쟁 조건으로 인한 무제한 무료 이용보다 안전하다.
        boolean isFreeRetry = false;
        if (request.retryOfId() != null) {
            int claimed = gradeResultRepository.claimRetry(request.retryOfId(), userId, GradeStatus.QUALITY_FAIL);
            if (claimed > 0) {
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
        GradeResult gradeResult = buildGradeResult(
                userId, visionResult, isFree,
                isFreeRetry ? request.retryOfId() : null, imageHash);
        try {
            gradeResultRepository.save(gradeResult);
        } catch (DataIntegrityViolationException e) {
            // 위 캐시 조회 이후 저장 사이에 같은 사진으로 온 다른 요청이 먼저 커밋된 경우(더블클릭 등
            // TOCTOU 경쟁) - uq_grade_results_user_image_hash_success 유니크 인덱스가 막아준다. 아직
            // 포인트 차감 전이라 이중 과금은 없고, 먼저 저장된 그 결과를 대신 반환하면 된다.
            log.info("동일 이미지 경쟁 감지 - 먼저 저장된 SUCCESS 결과로 대체 반환: userId={}", userId);
            return gradeResultRepository
                    .findFirstByUserIdAndImageHashAndStatusOrderByCreatedAtDesc(userId, imageHash, GradeStatus.SUCCESS)
                    .map(this::buildCachedResponse)
                    .orElseThrow(() -> e);
        }

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

    // 업로드 순서가 항상 FRONT/BACK/CORNER_TL/TR/BL/BR로 고정이라 바이트를 이 순서대로 이어붙여 해시를
    // 낸다(uploadImages()가 같은 순서를 전제하는 것과 동일). Vision 호출·S3 업로드보다 먼저, 한 번만 읽는다.
    private String computeImageHash(List<MultipartFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (MultipartFile file : files) {
                digest.update(file.getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("이미지를 읽을 수 없습니다", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }

    // 캐시 히트 응답 - getGradeResult()와 동일하게 이미지 presigned URL을 다시 뽑아 반환하되,
    // 이번 요청은 포인트를 차감하지 않았으므로 remainingPoints는 null(변화 없음)로 두고, cached=true로
    // 표시해서 FE가 isFree/pointUsed(원본 진단 당시 값)를 "이번에 과금됨"으로 오해하지 않게 한다.
    private GradeResponse buildCachedResponse(GradeResult cached) {
        List<GradeResultImage> images = gradeResultImageRepository.findByGradeResultId(cached.getId());
        Map<String, String> imageUrls = images.stream()
                .collect(Collectors.toMap(
                        img -> img.getPhotoType().name(),
                        img -> s3FileStorage.generatePresignedUrl(img.getImageUrl()),
                        (existing, replacement) -> existing));
        return GradeResponse.from(cached, resolveCard(cached), imageUrls, null, true);
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
                        img -> s3FileStorage.generatePresignedUrl(img.getImageUrl()),
                        (existing, replacement) -> existing));

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
        for (MultipartFile file : request.files()) {
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
        List<MultipartFile> files = request.files();

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
        List<MultipartFile> files = request.files();

        try {
            return visionTimer.recordCallable(() -> {
                String response = chatClient.prompt()
                        .user(u -> {
                            u.text(buildPrompt());
                            files.forEach(file -> u.media(
                                    mimeTypeOf(file),
                                    toResource(file)));
                        })
                        // temperature 0 — 등급 산출은 창의성이 필요 없고, 기본값(1.0)으로 두면 같은 카드를
                        // 다시 진단했을 때 S/A처럼 등급이 달라져 사용자 신뢰가 깨질 수 있다. 0으로도 모델
                        // 내부 비결정성(활성화 순서 등)까지 완전히 없애진 못하지만 변동 폭을 크게 줄인다 -
                        // 완전한 재현성은 이미지 해시 캐싱(computeImageHash)이 보장한다.
                        // responseFormat(JSON_SCHEMA, strict) — 응답이 VISION_RESPONSE_SCHEMA를 벗어나지
                        // 못하게 API가 강제한다. 그래서 응답은 항상 순수 JSON이고 앞뒤에 설명 텍스트가
                        // 섞이지 않으므로, 예전처럼 문자열에서 '{'...'}'를 잘라내는 처리(extractJson)가
                        // 필요 없다 - 바로 역직렬화한다.
                        .options(OpenAiChatOptions.builder()
                                .model(gradeModel)
                                .temperature(0.0)
                                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                        .jsonSchema(VISION_RESPONSE_SCHEMA)
                                        .build()))
                        .call()
                        .content();

                return OBJECT_MAPPER.readValue(response, VisionResult.class);
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

                grade는 느낌으로 정하지 말고, centering_score/edge_score/surface_score/corner_score
                4개 중 가장 낮은 점수를 기준으로 판단하세요: 4개 모두 9.0 이상이면 S, 4개 모두 7.0
                이상이면 A, 4개 모두 5.0 이상이면 B. 그 미만이면 등급 없이 quality_issue를 검토하세요.

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

    private GradeResult buildGradeResult(Long userId, VisionResult vision,
                                         boolean isFree, Long retryOfId, String imageHash) {
        if (vision.qualityIssue()) {
            return GradeResult.builder()
                    .userId(userId)
                    .status(GradeStatus.QUALITY_FAIL)
                    .isFree(true)        // 품질 실패는 과금하지 않음
                    .pointUsed(0)
                    // 재업로드의 재업로드는 허용하지 않음 — 무한 무료 체인 방지
                    .retryAllowed(retryOfId == null)
                    .retryOfId(retryOfId)
                    .imageHash(imageHash)
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
                .imageHash(imageHash)
                .build();
    }

    public static class AiServiceUnavailableException extends RuntimeException {
        public AiServiceUnavailableException(String message) {
            super(message);
        }
    }
}
