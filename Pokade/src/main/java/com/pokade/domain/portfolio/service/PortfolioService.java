package com.pokade.domain.portfolio.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.portfolio.dto.PortfolioItemAddRequest;
import com.pokade.domain.portfolio.dto.PortfolioItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemUpdateRequest;
import com.pokade.domain.portfolio.entity.PortfolioItem;
import com.pokade.domain.portfolio.repository.PortfolioItemRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private final PortfolioItemRepository portfolioItemRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final CardPriceRepository cardPriceRepository;
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
        PortfolioItem item = portfolioItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND));

        portfolioItemRepository.delete(item);
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

            return PortfolioItemResponse.of(item, card, variant, price);
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

        return PortfolioItemResponse.of(item, card, variant, price);
    }
}
