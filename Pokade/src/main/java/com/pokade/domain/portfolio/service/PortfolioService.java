package com.pokade.domain.portfolio.service;

import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.ai.entity.GradeResultImage;
import com.pokade.domain.ai.entity.GradeStatus;
import com.pokade.domain.ai.entity.PhotoType;
import com.pokade.domain.ai.repository.GradeResultImageRepository;
import com.pokade.domain.ai.repository.GradeResultRepository;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.ExpansionRepository;
import com.pokade.domain.portfolio.dto.PortfolioAnalyticsItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioAnalyticsResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemAddRequest;
import com.pokade.domain.portfolio.dto.PortfolioItemPnlResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemUpdateRequest;
import com.pokade.domain.portfolio.dto.PortfolioSetCompletionResponse;
import com.pokade.domain.portfolio.dto.PortfolioSummaryResponse;
import com.pokade.domain.portfolio.entity.PortfolioItem;
import com.pokade.domain.portfolio.repository.PortfolioItemRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import com.pokade.global.port.UserAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private static final String RAW_PRICE_TYPE = "raw";
    private static final String EMPTY_GRADE = "";
    private static final String EMPTY_COMPANY = "";
    private static final String UNCLASSIFIED = "미분류";
    private static final String KRW = "KRW";

    private static final long THUMBNAIL_MAX_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> THUMBNAIL_ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final String THUMBNAIL_FOLDER = "portfolio";

    // card_prices는 카드에 따라 USD/JPY로 저장돼 있어 KRW 금액과 그대로 합산할 수 없다.
    // 실시간 환율 API가 없어 프론트(lib/currency.ts)와 동일한 고정 근사치를 사용한다.
    private static final Map<String, BigDecimal> FX_TO_KRW = Map.of(
            "KRW", BigDecimal.ONE,
            "USD", BigDecimal.valueOf(1400),
            "JPY", BigDecimal.valueOf(9));

    private final PortfolioItemRepository portfolioItemRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final CardPriceRepository cardPriceRepository;
    private final ExpansionRepository expansionRepository;
    private final GradeResultRepository gradeResultRepository;
    private final GradeResultImageRepository gradeResultImageRepository;
    private final S3FileStorage s3FileStorage;
    private final UserAccessChecker userAccessChecker;

    @Transactional
    public PortfolioItemResponse addItem(Long userId, PortfolioItemAddRequest request) {
        userAccessChecker.assertWritable(userId);

        Card card = cardRepository.findById(request.cardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        if (request.variantId() != null) {
            cardVariantRepository.findById(request.variantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        }

        PortfolioItem item = PortfolioItem.builder()
                .userId(userId)
                .cardId(request.cardId())
                .variantId(request.variantId())
                .quantity(request.quantity())
                .acquiredPrice(request.acquiredPrice())
                .acquiredAt(request.acquiredAt())
                .build();

        portfolioItemRepository.save(item);
        return enrichSingle(item, card);
    }

    public List<PortfolioItemResponse> getMyPortfolio(Long userId) {
        List<PortfolioItem> items = portfolioItemRepository.findByUserIdOrderByIdDesc(userId);
        if (items.isEmpty()) {
            return List.of();
        }
        return enrich(items);
    }

    @Transactional
    public PortfolioItemResponse updateItem(Long userId, Long itemId, PortfolioItemUpdateRequest request) {
        userAccessChecker.assertWritable(userId);

        PortfolioItem item = portfolioItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND));

        item.update(request.quantity(), request.acquiredPrice(), request.acquiredAt());

        Card card = cardRepository.findById(item.getCardId()).orElse(null);
        return enrichSingle(item, card);
    }

    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        userAccessChecker.assertWritable(userId);

        PortfolioItem item = portfolioItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND));

        portfolioItemRepository.delete(item);
    }

    // FR-PORT-02: 시세 없는 항목은 평가액 계산에서 제외한다(현재가를 모르면 전일가도 추정할 수 없음).
    // 전일가는 getGradeChart와 동일한 방식으로 change_1d_pct를 거슬러 올라가 추정한다(체결 이력 기반이 아닌 근사치).
    public PortfolioSummaryResponse getSummary(Long userId) {
        List<PortfolioItem> items = portfolioItemRepository.findByUserIdOrderByIdDesc(userId);
        if (items.isEmpty()) {
            return new PortfolioSummaryResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }

        Map<Long, Long> variantIdByItemId = resolveVariantIdsByItemId(items);
        Set<Long> variantIds = Set.copyOf(variantIdByItemId.values());

        Map<Long, CardPriceRepository.VariantMarketPriceView> priceMap = variantIds.isEmpty()
                ? Map.of()
                : cardPriceRepository.findMarketPricesByVariantIds(
                        new ArrayList<>(variantIds), RAW_PRICE_TYPE, EMPTY_GRADE, EMPTY_COMPANY)
                        .stream()
                        .collect(Collectors.toMap(
                                CardPriceRepository.VariantMarketPriceView::getVariantId, v -> v));

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal previousValue = BigDecimal.ZERO;
        String currency = null;

        for (PortfolioItem item : items) {
            Long variantId = variantIdByItemId.get(item.getId());
            CardPriceRepository.VariantMarketPriceView price = variantId != null ? priceMap.get(variantId) : null;
            if (price == null || price.getMarket() == null) {
                continue;
            }
            BigDecimal marketInKrw = toKrw(price.getMarket(), price.getCurrency());
            if (marketInKrw == null) {
                continue;
            }

            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
            totalValue = totalValue.add(marketInKrw.multiply(quantity));
            currency = KRW;

            BigDecimal change1dPct = price.getChange1dPct();
            BigDecimal divisor = change1dPct == null
                    ? BigDecimal.ONE
                    : BigDecimal.ONE.add(change1dPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal previousUnitPrice = divisor.signum() == 0
                    ? marketInKrw
                    : marketInKrw.divide(divisor, 2, RoundingMode.HALF_UP);
            previousValue = previousValue.add(previousUnitPrice.multiply(quantity));
        }

        BigDecimal changeAmount = totalValue.subtract(previousValue).setScale(0, RoundingMode.HALF_UP);
        BigDecimal changeRate = previousValue.signum() == 0
                ? BigDecimal.ZERO
                : changeAmount.divide(previousValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        return new PortfolioSummaryResponse(totalValue.setScale(0, RoundingMode.HALF_UP), changeAmount, changeRate, currency);
    }

    // FR-PORT-05: 취득가(플랫폼 거래로 취득 시 체결가가 그대로 저장됨) 대비 현재 시세로 손익을 계산한다.
    public PortfolioItemPnlResponse getPnl(Long userId, Long itemId) {
        PortfolioItem item = portfolioItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND));

        if (item.getAcquiredPrice() == null) {
            throw new BusinessException(ErrorCode.PORTFOLIO_ACQUIRED_PRICE_REQUIRED);
        }

        Long resolvedVariantId = item.getVariantId() != null
                ? item.getVariantId()
                : cardVariantRepository.findPrimaryVariantId(item.getCardId()).orElse(null);

        CardPriceRepository.VariantMarketPriceView price = resolvedVariantId != null
                ? cardPriceRepository.findMarketPricesByVariantIds(
                        List.of(resolvedVariantId), RAW_PRICE_TYPE, EMPTY_GRADE, EMPTY_COMPANY)
                        .stream().findFirst().orElse(null)
                : null;

        if (price == null || price.getMarket() == null) {
            throw new BusinessException(ErrorCode.PORTFOLIO_PRICE_NOT_FOUND);
        }
        BigDecimal marketInKrw = toKrw(price.getMarket(), price.getCurrency());
        if (marketInKrw == null) {
            throw new BusinessException(ErrorCode.PORTFOLIO_PRICE_NOT_FOUND);
        }

        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
        BigDecimal acquiredTotal = BigDecimal.valueOf(item.getAcquiredPrice()).multiply(quantity);
        BigDecimal currentTotal = marketInKrw.multiply(quantity);
        BigDecimal pnlAmount = currentTotal.subtract(acquiredTotal).setScale(0, RoundingMode.HALF_UP);
        BigDecimal pnlRate = acquiredTotal.signum() == 0
                ? BigDecimal.ZERO
                : pnlAmount.divide(acquiredTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

        return new PortfolioItemPnlResponse(
                item.getId(), item.getCardId(), item.getQuantity(), item.getAcquiredPrice(),
                marketInKrw, KRW, pnlAmount, pnlRate);
    }

    // FR-PORT-06: 세트별·레어도별 구성 비율을 계산한다. 시세 유무와 무관하게 항상 계산 가능하도록
    // 평가액이 아닌 보유 수량(quantity) 기준으로 집계한다 - 시세가 없는 카드도 세트/레어도는
    // 이미 알고 있으므로, 시세 없는 항목만 있을 때 결과가 통째로 비어버리는 걸 막는다.
    public PortfolioAnalyticsResponse getAnalytics(Long userId) {
        List<PortfolioItem> items = portfolioItemRepository.findByUserIdOrderByIdDesc(userId);
        if (items.isEmpty()) {
            return new PortfolioAnalyticsResponse(List.of(), List.of());
        }

        Set<Long> cardIds = items.stream().map(PortfolioItem::getCardId).collect(Collectors.toSet());
        Map<Long, Card> cardMap = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        Map<String, BigDecimal> countBySet = new LinkedHashMap<>();
        Map<String, BigDecimal> countByRarity = new LinkedHashMap<>();
        BigDecimal totalCount = BigDecimal.ZERO;
        for (PortfolioItem item : items) {
            Card card = cardMap.get(item.getCardId());
            String setKey = card != null && card.getSetName() != null ? card.getSetName() : UNCLASSIFIED;
            String rarityKey = card != null && card.getRarity() != null ? card.getRarity() : UNCLASSIFIED;
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());

            countBySet.merge(setKey, quantity, BigDecimal::add);
            countByRarity.merge(rarityKey, quantity, BigDecimal::add);
            totalCount = totalCount.add(quantity);
        }

        return new PortfolioAnalyticsResponse(
                toAnalyticsItems(countBySet, totalCount),
                toAnalyticsItems(countByRarity, totalCount));
    }

    private List<PortfolioAnalyticsItemResponse> toAnalyticsItems(Map<String, BigDecimal> countByLabel, BigDecimal totalCount) {
        return countByLabel.entrySet().stream()
                .map(entry -> new PortfolioAnalyticsItemResponse(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue().divide(totalCount, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(PortfolioAnalyticsItemResponse::value).reversed())
                .toList();
    }

    // FR-PORT-07: 세트 완성도 - 사용자가 보유한 서로 다른 카드가 각 세트 전체 카드 중 몇 %인지 계산한다.
    // 수량(quantity)은 완성도와 무관하므로(같은 카드를 여러 장 가져도 완성도는 그대로) distinct cardId 기준으로 센다.
    // expansion이 없는 카드(비정규화된 setName만 있는 구버전 데이터 등)는 전체 카드 수를 알 수 없어 제외한다.
    public List<PortfolioSetCompletionResponse> getSetCompletion(Long userId) {
        List<PortfolioItem> items = portfolioItemRepository.findByUserIdOrderByIdDesc(userId);
        if (items.isEmpty()) {
            return List.of();
        }

        Set<Long> cardIds = items.stream().map(PortfolioItem::getCardId).collect(Collectors.toSet());
        Map<Long, Card> cardMap = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        Map<String, Set<Long>> ownedCardIdsByExpansion = new LinkedHashMap<>();
        for (Long cardId : cardIds) {
            Card card = cardMap.get(cardId);
            Expansion expansion = card != null ? card.getExpansion() : null;
            if (expansion == null) {
                continue;
            }
            ownedCardIdsByExpansion.computeIfAbsent(expansion.getId(), k -> new HashSet<>()).add(cardId);
        }

        if (ownedCardIdsByExpansion.isEmpty()) {
            return List.of();
        }

        Map<String, Expansion> expansionMap = expansionRepository.findAllById(ownedCardIdsByExpansion.keySet()).stream()
                .collect(Collectors.toMap(Expansion::getId, e -> e));

        // Expansion.total이 비어 있는(동기화 누락) 세트는 실제 DB에 적재된 카드 수로 대신한다.
        Map<String, Long> dbCountByExpansion = cardRepository.findCardCountsByExpansion().stream()
                .collect(Collectors.toMap(
                        CardRepository.ExpansionCardCountView::getExpansionId,
                        CardRepository.ExpansionCardCountView::getCount));

        return ownedCardIdsByExpansion.entrySet().stream()
                .map(entry -> {
                    String expansionId = entry.getKey();
                    Expansion expansion = expansionMap.get(expansionId);
                    int ownedCount = entry.getValue().size();
                    Integer total = expansion != null ? expansion.getTotal() : null;
                    int totalCount = total != null ? total : dbCountByExpansion.getOrDefault(expansionId, 0L).intValue();
                    // Expansion.total은 외부 동기화 값이라 실제 적재된 카드 수보다 작게 들어올 수 있다 -
                    // 그 경우에도 완성도가 100%를 넘지 않도록 ownedCount를 totalCount로 clamp한다.
                    BigDecimal completionRate = totalCount <= 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(Math.min(ownedCount, totalCount))
                                    .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP);

                    return new PortfolioSetCompletionResponse(
                            expansionId,
                            expansion != null ? expansion.getName() : UNCLASSIFIED,
                            ownedCount,
                            totalCount,
                            completionRate);
                })
                .sorted(Comparator.comparing(PortfolioSetCompletionResponse::completionRate).reversed())
                .toList();
    }

    // 지원하지 않는 통화면 null을 반환한다 - 환율 1배로 조용히 잘못된 값을 합산하지 않기 위함(프론트 toKrw()와 동일한 원칙).
    private BigDecimal toKrw(BigDecimal amount, String currency) {
        BigDecimal rate = currency != null ? FX_TO_KRW.get(currency) : null;
        return rate != null ? amount.multiply(rate) : null;
    }

    // variantId가 명시된 항목은 그대로, null인 항목은 카드의 대표 변형으로 일괄 해석한다(enrich()의 변형 해석 로직과 동일한 규칙).
    private Map<Long, Long> resolveVariantIdsByItemId(List<PortfolioItem> items) {
        Set<Long> nullVariantCardIds = items.stream()
                .filter(i -> i.getVariantId() == null)
                .map(PortfolioItem::getCardId)
                .collect(Collectors.toSet());

        Map<Long, Long> primaryVariantByCard = nullVariantCardIds.isEmpty()
                ? Map.of()
                : cardVariantRepository.findPrimaryVariantIdsByCardIds(new ArrayList<>(nullVariantCardIds))
                        .stream()
                        .collect(Collectors.toMap(
                                CardVariantRepository.PrimaryVariantIdView::getCardId,
                                CardVariantRepository.PrimaryVariantIdView::getVariantId));

        Map<Long, Long> resolved = new HashMap<>();
        for (PortfolioItem item : items) {
            Long variantId = item.getVariantId() != null ? item.getVariantId() : primaryVariantByCard.get(item.getCardId());
            if (variantId != null) {
                resolved.put(item.getId(), variantId);
            }
        }
        return resolved;
    }

    /**
     * 거래 완료(구매 확정) 시 포트폴리오에 자동으로 카드를 추가한다.
     * TradeService.confirmTrade()에서 호출된다.
     * 동일 trade_id로 이미 등록된 경우 멱등성 보장을 위해 무시한다.
     */
    @Transactional
    public void addFromCompletedTrade(Long buyerId, Long tradeId, Long cardId, Long variantId, Integer price) {
        if (portfolioItemRepository.existsByTradeId(tradeId)) {
            return;
        }

        PortfolioItem item = PortfolioItem.builder()
                .userId(buyerId)
                .cardId(cardId)
                .variantId(variantId)
                .quantity(1)
                .acquiredPrice(price)
                .acquiredAt(LocalDateTime.now())
                .tradeId(tradeId)
                .build();

        portfolioItemRepository.save(item);
    }

    // FR-AI-04: AI 등급 진단 결과를 바탕으로 도감에 카드를 등록한다.
    // 도감 등록은 유저 선택(자동 아님) — 컨트롤러가 이 메서드를 명시적 요청에서만 호출한다.
    // override(cardId/variantId)는 AI가 카드를 인식하지 못했거나(cardId/visionCardId 둘 다 null)
    // 잘못 인식한 경우, 사용자가 직접 고른 카드로 등록할 수 있게 한다 — null이면 기존 AI 인식 결과를 그대로 쓴다.
    @Transactional
    public PortfolioItemResponse addFromGradeResult(Long userId, Long resultId, Long overrideCardId, Long overrideVariantId) {
        userAccessChecker.assertWritable(userId);

        GradeResult gradeResult = gradeResultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GRADE_RESULT_NOT_FOUND));

        if (!gradeResult.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (gradeResult.getStatus() != GradeStatus.SUCCESS || gradeResult.getGrade() == null) {
            throw new BusinessException(ErrorCode.GRADE_RESULT_NOT_REGISTRABLE);
        }

        if (portfolioItemRepository.existsByGradeResultId(resultId)) {
            throw new BusinessException(ErrorCode.GRADE_RESULT_ALREADY_REGISTERED);
        }

        Long cardId;
        Card card;
        if (overrideCardId != null) {
            cardId = overrideCardId;
            card = cardRepository.findById(cardId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        } else {
            // cardId/variantId는 카드 자동식별 연동 전까지 채워지지 않을 수 있어(현재 Vision은
            // vision_card_id(externalId)만 반환), 저장된 값이 없으면 그 자리에서 externalId로 해석한다.
            cardId = gradeResult.getCardId();
            if (cardId != null) {
                card = cardRepository.findById(cardId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
            } else if (gradeResult.getVisionCardId() != null) {
                card = cardRepository.findByExternalId(gradeResult.getVisionCardId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
                cardId = card.getId();
            } else {
                throw new BusinessException(ErrorCode.CARD_NOT_FOUND);
            }
        }

        Long variantId;
        if (overrideVariantId != null) {
            cardVariantRepository.findById(overrideVariantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
            variantId = overrideVariantId;
        } else if (overrideCardId != null) {
            // 카드를 직접 지정한 경우 원래 진단의 variantId는 다른 카드 기준이라 재사용하지 않는다.
            variantId = cardVariantRepository.findPrimaryVariantId(cardId).orElse(null);
        } else {
            variantId = gradeResult.getVariantId() != null
                    ? gradeResult.getVariantId()
                    : cardVariantRepository.findPrimaryVariantId(cardId).orElse(null);
        }

        PortfolioItem item = PortfolioItem.builder()
                .userId(userId)
                .cardId(cardId)
                .variantId(variantId)
                .quantity(1)
                .acquiredAt(LocalDateTime.now())
                .gradeResultId(resultId)
                // 카드 인식 성공/실패·정정 여부와 무관하게, 사용자가 실제로 찍은 그 카드 사진을 표지로 쓴다.
                .thumbnailKey(resolveFrontImageKey(resultId))
                .build();

        portfolioItemRepository.save(item);
        return enrichSingle(item, card);
    }

    // 도감 항목의 표지 사진을 사용자가 직접 업로드한 이미지로 교체한다 - AI 진단으로 등록됐는지와 무관하게 항상 가능.
    // ponytail: 이전 커스텀 표지 S3 객체는 정리하지 않는다(고아 객체 누적) - 필요해지면
    // ProfileImageService의 AFTER_COMMIT 정리 이벤트 패턴을 그대로 재사용해 업그레이드.
    @Transactional
    public PortfolioItemResponse setThumbnail(Long userId, Long itemId, MultipartFile file) {
        userAccessChecker.assertWritable(userId);
        validateThumbnail(file);

        PortfolioItem item = portfolioItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND));

        item.changeThumbnail(s3FileStorage.upload(file, THUMBNAIL_FOLDER));

        Card card = cardRepository.findById(item.getCardId()).orElse(null);
        return enrichSingle(item, card);
    }

    private void validateThumbnail(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > THUMBNAIL_MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!THUMBNAIL_ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private String resolveFrontImageKey(Long gradeResultId) {
        return gradeResultImageRepository.findByGradeResultId(gradeResultId).stream()
                .filter(img -> img.getPhotoType() == PhotoType.FRONT)
                .map(GradeResultImage::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    private String resolveThumbnailUrl(PortfolioItem item) {
        return item.getThumbnailKey() != null ? s3FileStorage.generatePresignedUrl(item.getThumbnailKey()) : null;
    }

    // ─── 내부 enrichment 헬퍼 ────────────────────────────────────────────────

    private List<PortfolioItemResponse> enrich(List<PortfolioItem> items) {
        // 1. 카드 일괄 조회
        Set<Long> cardIds = items.stream().map(PortfolioItem::getCardId).collect(Collectors.toSet());
        Map<Long, Card> cardMap = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        // 2. variantId 결정: 명시된 variant → 그대로 / null → 대표 변형으로 조회
        Set<Long> explicitVariantIds = items.stream()
                .filter(i -> i.getVariantId() != null)
                .map(PortfolioItem::getVariantId)
                .collect(Collectors.toSet());

        Set<Long> nullVariantCardIds = items.stream()
                .filter(i -> i.getVariantId() == null)
                .map(PortfolioItem::getCardId)
                .collect(Collectors.toSet());

        // null variantId 아이템을 위한 cardId→primaryVariantId 매핑
        Map<Long, Long> primaryVariantByCard = nullVariantCardIds.isEmpty()
                ? Map.of()
                : cardVariantRepository.findPrimaryVariantIdsByCardIds(new ArrayList<>(nullVariantCardIds))
                        .stream()
                        .collect(Collectors.toMap(
                                CardVariantRepository.PrimaryVariantIdView::getCardId,
                                CardVariantRepository.PrimaryVariantIdView::getVariantId));

        // 시세 조회에 쓸 최종 variantId 집합
        Set<Long> priceQueryVariantIds = new java.util.HashSet<>(explicitVariantIds);
        priceQueryVariantIds.addAll(primaryVariantByCard.values());

        // 3. variant 일괄 조회
        Map<Long, CardVariant> variantMap = priceQueryVariantIds.isEmpty()
                ? Map.of()
                : cardVariantRepository.findAllById(priceQueryVariantIds).stream()
                        .collect(Collectors.toMap(CardVariant::getId, v -> v));

        // 4. 시세 일괄 조회 (raw NM 기준)
        Map<Long, CardPriceRepository.VariantMarketPriceView> priceMap = priceQueryVariantIds.isEmpty()
                ? Map.of()
                : cardPriceRepository.findMarketPricesByVariantIds(
                        new ArrayList<>(priceQueryVariantIds), RAW_PRICE_TYPE, EMPTY_GRADE, EMPTY_COMPANY)
                        .stream()
                        .collect(Collectors.toMap(
                                CardPriceRepository.VariantMarketPriceView::getVariantId,
                                v -> v));

        // 5. 조립
        return items.stream().map(item -> {
            Card card = cardMap.get(item.getCardId());

            Long resolvedVariantId = item.getVariantId() != null
                    ? item.getVariantId()
                    : primaryVariantByCard.get(item.getCardId());

            CardVariant variant = resolvedVariantId != null ? variantMap.get(resolvedVariantId) : null;
            CardPriceRepository.VariantMarketPriceView price = resolvedVariantId != null
                    ? priceMap.get(resolvedVariantId)
                    : null;

            return PortfolioItemResponse.of(item, card, variant, price, resolveThumbnailUrl(item));
        }).toList();
    }

    private PortfolioItemResponse enrichSingle(PortfolioItem item, Card card) {
        Long resolvedVariantId = item.getVariantId() != null
                ? item.getVariantId()
                : cardVariantRepository.findPrimaryVariantId(item.getCardId()).orElse(null);

        CardVariant variant = resolvedVariantId != null
                ? cardVariantRepository.findById(resolvedVariantId).orElse(null)
                : null;

        CardPriceRepository.VariantMarketPriceView price = null;
        if (resolvedVariantId != null) {
            List<CardPriceRepository.VariantMarketPriceView> prices = cardPriceRepository
                    .findMarketPricesByVariantIds(List.of(resolvedVariantId), RAW_PRICE_TYPE, EMPTY_GRADE, EMPTY_COMPANY);
            price = prices.isEmpty() ? null : prices.get(0);
        }

        return PortfolioItemResponse.of(item, card, variant, price, resolveThumbnailUrl(item));
    }
}
