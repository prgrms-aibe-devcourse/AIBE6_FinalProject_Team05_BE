package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 재입고 알림을 이미 보낸(listingNotified=true) 워치리스트를 훑어, 실제로 매물이 다시 소진됐는지(활성
// 매물 0개) 확인하고 소진됐으면 리셋한다(#300 후속) - 다음 재입고 때 또 알릴 수 있게 하는 목적.
//
// 이벤트 기반(매물이 사라지는 시점마다 즉시 감지) 대신 배치로 만든 이유: 매물이 ACTIVE를 벗어나는
// 지점이 listing 도메인(판매자 취소)뿐 아니라 trade 도메인(구매 시작)·admin 도메인(숨김 처리)에도
// 흩어져 있어, 이벤트 발행을 전부 커버하려면 3개 도메인에 손을 대야 한다. 배치는 그중 몇 곳에서
// ACTIVE를 벗어났는지와 무관하게 "지금 활성 매물이 있는지"만 다시 확인하면 되므로 도메인 결합이 없다.
//
// 배치 주기(30분, 목표가 배치보다 짧게)의 근거: 리셋이 늦어지면 단순히 "리셋이 늦게 반영"되는 게
// 아니라, 그 지연 구간 안에 실제로 재입고가 발생하면 listingNotified가 아직 true라 그 재입고에 대한
// 알림이 완전히 누락된다(다음 리셋 전까지 재시도되지 않음). 목표가 배치(1시간, 지연돼도 결국 언젠가
// 알림이 감)보다 리셋 배치의 지연 비용이 더 크다고 판단해 더 짧은 주기로 시작한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistListingNotifiedResetScheduler {

    private final WatchlistRepository watchlistRepository;
    private final CardVariantRepository cardVariantRepository;
    private final WatchlistListingNotifiedResetProcessor processor;

    @Scheduled(cron = "0 0,30 * * * *")
    public void resetListingNotifiedIfSoldOut() {
        List<Watchlist> watchlists = watchlistRepository.findByListingNotifiedTrue();
        if (watchlists.isEmpty()) {
            return;
        }

        // variantId가 null(대표 변형 관심)인 워치리스트가 여러 개여도 카드별로 한 번만 조회하도록
        // 배치 조회한다(WatchlistTargetPriceNoticeScheduler의 cardById/allTimeRangeByCardId와 동일한 패턴).
        List<Long> cardIds = watchlists.stream().map(Watchlist::getCardId).distinct().toList();
        Map<Long, Long> primaryVariantIdByCardId = cardVariantRepository.findPrimaryVariantIdsByCardIds(cardIds).stream()
                .collect(Collectors.toMap(CardVariantRepository.PrimaryVariantIdView::getCardId,
                        CardVariantRepository.PrimaryVariantIdView::getVariantId));

        for (Watchlist watchlist : watchlists) {
            try {
                Long resolvedVariantId = WatchlistVariantResolver.resolveOrPrimary(
                        watchlist.getVariantId(), primaryVariantIdByCardId.get(watchlist.getCardId()));
                processor.process(watchlist.getId(), resolvedVariantId);
            } catch (Exception e) {
                log.warn("워치리스트 재입고 알림 리셋 확인 실패: watchlistId={}", watchlist.getId(), e);
            }
        }
    }
}
