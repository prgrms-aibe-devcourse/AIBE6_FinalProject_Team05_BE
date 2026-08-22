package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.dto.WatchlistCountResponse;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.dto.WatchlistUpdateRequest;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    private static final long WATCHLIST_LIMIT = 20;
    // CardQueryService.MAX_FILTER_VALUES와 같은 취지 - 한 번에 조회 가능한 카드 수 상한을 둬서 과도한 IN절을 막는다.
    private static final int MAX_COUNT_CARD_IDS = 100;

    private final WatchlistRepository watchlistRepository;
    private final PriceService priceService;
    private final CardRepository cardRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;
    private final CardNameKoResolver cardNameKoResolver;
    private final WatchlistTargetPriceEvaluator watchlistTargetPriceEvaluator;

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님
    @Timed(value = "watchlist.add.duration")
    @Transactional
    public WatchlistResponse addWatchlist(Long userId, WatchlistCreateRequest request) {
        // #308: 목표가는 등록 시점에 선택 입력으로 변경됨 - 둘 다 없이 등록 가능(수정 API는 별개, updateWatchlist() 참고).

        // 동시 등록 요청에서 "중복 체크 + 20개 제한 체크 + 저장" 구간이 원자적이도록, 같은 유저의 요청만
        // 트랜잭션 종료까지 직렬화한다(다른 유저는 영향 없음).
        watchlistRepository.acquireUserLock(userId);

        if (watchlistRepository.existsByUserIdAndCardId(userId, request.cardId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_WATCHLIST);
        }

        if (watchlistRepository.countByUserId(userId) >= WATCHLIST_LIMIT) {
            throw new BusinessException(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
        }

        validateTargetPriceOrder(request.targetBuyPrice(), request.targetSellPrice());

        Watchlist watchlist = Watchlist.builder()
                .userId(userId)
                .cardId(request.cardId())
                .variantId(request.variantId())
                .targetBuyPrice(request.targetBuyPrice())
                .targetSellPrice(request.targetSellPrice())
                .build();

        // 위 잠금으로 정상 경로에서는 걸릴 일이 없지만, 방어적으로 DB UNIQUE 제약 위반도 안전하게 변환한다.
        try {
            Watchlist saved = watchlistRepository.save(watchlist);

            // TODO(#275): updateWatchlist()/getWatchlist()와 같은 원인(전체 기간 range 사용)을 공유하지만,
            // "등록 시점에 이미 도달이면 배치를 기다리지 않고 즉시 알림"이라는 의도된 최적화(아래 주석)와
            // 상충할 수 있어 이번엔 범위에서 제외함 - 등록 이후 스코프로 바꿀지는 별도 논의 필요.
            PriceTradeStatsRepository.CardPriceRangeView range = priceTradeStatsRepository
                    .findPriceRangesByCardIds(List.of(saved.getCardId()), null, TradeStatus.COMPLETED)
                    .stream()
                    .findFirst()
                    .orElse(null);
            Integer reachedTargetPrice = watchlistTargetPriceEvaluator.resolveReachedTargetPrice(saved, range);
            boolean targetReached = reachedTargetPrice != null;
            // 등록 시점에 이미 목표가 범위 안이면 배치(최대 1시간 지연)를 기다리지 않고 바로 알림 처리한다 -
            // 화면은 "도달"인데 실제 알림은 한참 뒤에 오는 시차, 그리고 알림 자체가 생성 안 되는 누락을 없애기 위함.
            watchlistTargetPriceEvaluator.notifyIfNewlyReached(saved, reachedTargetPrice);
            return WatchlistResponse.of(saved, targetReached);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_WATCHLIST);
        }
    }

    public List<WatchlistResponse> getWatchlist(Long userId) {
        List<Watchlist> watchlists = watchlistRepository.findByUserId(userId);
        if (watchlists.isEmpty()) {
            return List.of();
        }
        
        List<Long> cardIds = watchlists.stream().map(Watchlist::getCardId).distinct().toList();
        Map<Long, CardPriceSummaryResponse> priceByCardId = priceService.getSummaries(cardIds, null, true)
                .stream()
                .collect(Collectors.toMap(CardPriceSummaryResponse::cardId, Function.identity()));
        Map<Long, Card> cardById = cardRepository.findAllById(cardIds)
                .stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

        // #275: 카드마다 "이 워치리스트가 등록된 시점(createdAt) 이후" 체결분만으로 도달을 판정해야 한다
        // (전체 기간으로 보면 과거 어느 시점에 목표가 범위를 스쳤다는 이유만으로 잘못 판정될 수 있음 - 배치
        // 판정 경로(WatchlistTargetPriceNoticeProcessor)와 동일한 스코프로 통일). 한 유저는 같은 카드를
        // 중복 등록할 수 없어(cardId 유니크) cardIds와 sinceList를 같은 순서로 안전하게 페어링할 수 있다.
        Map<Long, LocalDateTime> createdAtByCardId = watchlists.stream()
                .collect(Collectors.toMap(Watchlist::getCardId, watchlistTargetPriceEvaluator::resolveWatchScopeStart, (a, b) -> a));
        List<LocalDateTime> sinceList = cardIds.stream().map(createdAtByCardId::get).toList();
        Map<Long, PriceTradeStatsRepository.CardPriceRangeView> rangeByCardId =
                priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(cardIds, sinceList, null, TradeStatus.COMPLETED)
                        .stream()
                        .collect(Collectors.toMap(PriceTradeStatsRepository.CardPriceRangeView::getCardId, Function.identity()));
        // "등락" 배지용 - 최근 7일 vs 이전 7일 S등급 평균 체결가 비교(%). getStats()/getRanking()과 같은 기준.
        Map<Long, BigDecimal> changeRateByCardId = priceService.getChangeRates(cardIds);

        return watchlists.stream()
                .map(watchlist -> {
                    Card card = cardById.get(watchlist.getCardId());
                    return WatchlistResponse.withPrice(
                            watchlist,
                            card,
                            card != null ? cardNameKoResolver.resolve(card) : null,
                            priceByCardId.get(watchlist.getCardId()),
                            changeRateByCardId.get(watchlist.getCardId()),
                            isTargetReached(watchlist, rangeByCardId.get(watchlist.getCardId())));
                })
                .toList();
    }

    private boolean isTargetReached(Watchlist watchlist, PriceTradeStatsRepository.CardPriceRangeView range) {
        return watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, range) != null;
    }

    // 임시 계측 - #258, 팀 논의 전 커밋 대상 아님
    @Timed(value = "watchlist.update.duration")
    @Transactional
    public WatchlistResponse updateWatchlist(Long userId, Long watchlistId, WatchlistUpdateRequest request) {
        boolean resend = Boolean.TRUE.equals(request.resendNotification());
        // 재알림만 요청할 때는 가격을 안 보낼 수 있음 - 필수 검증에서 예외로 취급
        if (!resend) {
            validateAtLeastOneTargetPrice(request.targetBuyPrice(), request.targetSellPrice());
        }

        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        // 부분 수정이라 요청 값만 봐서는 역전을 못 잡는다 - 구매가만 올려 보내도 "기존 판매가"와 엮여
        // 역전될 수 있으므로, updateTargetPrices()와 같은 규칙으로 최종 조합을 먼저 계산해 검증한다.
        // 가격을 하나도 안 보낸 요청(재알림 전용)은 건드리지 않고 넘긴다 - 이번 검증이 생기기 전에
        // 저장된 역전 데이터가 있어도 재알림 요청까지 막히지는 않게 한다.
        if (request.targetBuyPrice() != null || request.targetSellPrice() != null) {
            validateTargetPriceOrder(
                    Watchlist.resolveUpdatedPrice(request.targetBuyPrice(), watchlist.getTargetBuyPrice()),
                    Watchlist.resolveUpdatedPrice(request.targetSellPrice(), watchlist.getTargetSellPrice()));
        }

        watchlist.updateTargetPrices(request.targetBuyPrice(), request.targetSellPrice());
        if (resend) {
            watchlist.requestNotificationAgain();
        }

        // #275: 전체 기간이 아니라 "이 워치리스트 등록 시점 이후" 체결분만으로 재판정한다 - 목표가를
        // 수정해도 과거(등록 훨씬 전) 한때 그 가격대였다는 이유만으로 즉시 "도달"로 오판정되던 버그 수정.
        // getWatchlist()/배치(WatchlistTargetPriceNoticeProcessor)와 동일한 스코프로 통일.
        PriceTradeStatsRepository.CardPriceRangeView range = priceTradeStatsRepository
                .findPriceRangesByCardIdsSincePerCard(
                        List.of(watchlist.getCardId()), List.of(watchlistTargetPriceEvaluator.resolveWatchScopeStart(watchlist)), null, TradeStatus.COMPLETED)
                .stream()
                .findFirst()
                .orElse(null);
        Integer reachedTargetPrice = watchlistTargetPriceEvaluator.resolveReachedTargetPrice(watchlist, range);
        boolean targetReached = reachedTargetPrice != null;
        watchlistTargetPriceEvaluator.notifyIfNewlyReached(watchlist, reachedTargetPrice);
        return WatchlistResponse.of(watchlist, targetReached);
    }

    // #238: 목표 구매가가 판매가 이상이면 두 목표가가 한 체결가에 동시에 걸려 알림이 의미를 잃는다.
    // 한쪽만 설정된 경우는 애초에 역전이 성립하지 않으므로 통과시킨다.
    private void validateTargetPriceOrder(Integer targetBuyPrice, Integer targetSellPrice) {
        if (targetBuyPrice == null || targetSellPrice == null) {
            return;
        }
        if (targetBuyPrice >= targetSellPrice) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_PRICE_RANGE);
        }
    }

    private void validateAtLeastOneTargetPrice(Integer targetBuyPrice, Integer targetSellPrice) {
        if (targetBuyPrice == null && targetSellPrice == null) {
            throw new BusinessException(ErrorCode.TARGET_PRICE_REQUIRED);
        }
    }

    // 카드별 관심수(워치리스트 등록 수) 배치 조회 - 카드 수와 무관하게 쿼리 1회로 처리한다(getWatchlist()의
    // IN절 + Map 그룹핑 패턴과 동일). 등록이 하나도 없는 카드는 응답에서 제외되지 않고 0으로 채워진다.
    public List<WatchlistCountResponse> getWatchlistCounts(List<Long> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "cardIds는 최소 1개 이상 지정해야 합니다.");
        }
        List<Long> distinctCardIds = cardIds.stream().distinct().toList();
        if (distinctCardIds.size() > MAX_COUNT_CARD_IDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "cardIds는 최대 " + MAX_COUNT_CARD_IDS + "개까지 지정할 수 있습니다.");
        }

        Map<Long, Long> countByCardId = watchlistRepository.countGroupedByCardIdIn(distinctCardIds).stream()
                .collect(Collectors.toMap(
                        WatchlistRepository.WatchlistCardCountView::getCardId,
                        WatchlistRepository.WatchlistCardCountView::getCount));

        return distinctCardIds.stream()
                .map(cardId -> new WatchlistCountResponse(cardId, countByCardId.getOrDefault(cardId, 0L)))
                .toList();
    }

    @Transactional
    public void deleteWatchlist(Long userId, Long watchlistId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));

        watchlistRepository.delete(watchlist);
    }
}
