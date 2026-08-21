package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 워치리스트 목표가(구매/판매) 도달 감지 배치. 1시간마다 미알림 워치리스트를 훑어
// 등록(createdAt) 이후 체결분만으로 목표가 도달 여부를 재확인하고, 도달 시 알림을 남긴다.
// 스케줄러 메서드 자체에는 @Transactional을 걸지 않는다 - 워치리스트 1건마다 독립된 트랜잭션으로 처리해야
// 하나의 실패가 이전에 커밋된 다른 건들까지 롤백시키지 않으므로, 실제 처리는 별도 빈(WatchlistTargetPriceNoticeProcessor)의
// REQUIRES_NEW 메서드에 위임한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistTargetPriceNoticeScheduler {

    private final WatchlistRepository watchlistRepository;
    private final CardRepository cardRepository;
    private final PriceTradeStatsRepository priceTradeStatsRepository;
    private final WatchlistTargetPriceNoticeProcessor processor;

    @Scheduled(cron = "0 15 * * * *")
    public void detectTargetPriceReached() {
        List<Watchlist> watchlists = watchlistRepository.findByIsNotifiedFalse();
        if (watchlists.isEmpty()) {
            return;
        }

        List<Long> cardIds = watchlists.stream().map(Watchlist::getCardId).distinct().toList();
        Map<Long, Card> cardById = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        // 1차 필터: 전체 기간 최저~최고가로 "애초에 목표가 근처도 못 간" 워치리스트를 걸러내
        // 워치리스트별 개별 조회(2차)를 후보로만 좁힌다 (N+1 최소화).
        Map<Long, PriceTradeStatsRepository.CardPriceRangeView> allTimeRangeByCardId =
                priceTradeStatsRepository.findPriceRangesByCardIds(cardIds, null, TradeStatus.COMPLETED).stream()
                        .collect(Collectors.toMap(PriceTradeStatsRepository.CardPriceRangeView::getCardId, Function.identity()));

        for (Watchlist watchlist : watchlists) {
            try {
                processor.process(watchlist.getId(), cardById.get(watchlist.getCardId()), allTimeRangeByCardId.get(watchlist.getCardId()));
            } catch (Exception e) {
                log.warn("워치리스트 목표가 도달 감지 실패: watchlistId={}", watchlist.getId(), e);
            }
        }
    }
}
