package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

// #308 조사에서 확인된 공백을 메우는 테스트 - resolveReachedTargetPrice()를 mock이 아니라
// 실제 로직으로 직접 호출해, "목표가 둘 다 null" 분기가 range가 정상인 상태에서도 안전하게
// null을 반환하는지 검증한다(WatchlistServiceTest/Processor 계열은 evaluator를 mock으로
// 대체하거나 range 자체가 null인 경로만 태워서 이 분기를 실제로 통과시키지 않았음).
@ExtendWith(MockitoExtension.class)
class WatchlistTargetPriceEvaluatorTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock CardRepository cardRepository;
    @Mock NotificationService notificationService;
    @Mock CardNameKoResolver cardNameKoResolver;
    @InjectMocks WatchlistTargetPriceEvaluator evaluator;

    private record PriceRange(Long cardId, Integer minPrice, Integer maxPrice)
            implements PriceTradeStatsRepository.CardPriceRangeView {
        public Long getCardId() { return cardId; }
        public Integer getMinPrice() { return minPrice; }
        public Integer getMaxPrice() { return maxPrice; }
    }

    @Test
    @DisplayName("목표가가 둘 다 null이면 range가 정상이어도 도달 없음(null)을 반환한다")
    void resolveReachedTargetPrice_bothTargetPricesNull_returnsNull() {
        Watchlist watchlist = Watchlist.builder()
                .userId(1L).cardId(10L).targetBuyPrice(null).targetSellPrice(null).build();
        PriceRange range = new PriceRange(10L, 800, 1200);

        Integer result = evaluator.resolveReachedTargetPrice(watchlist, range);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("targetBuyPrice만 설정돼 있고 range 안에 들어오면 targetBuyPrice를 반환한다")
    void resolveReachedTargetPrice_onlyTargetBuyPriceInRange_returnsTargetBuyPrice() {
        Watchlist watchlist = Watchlist.builder()
                .userId(1L).cardId(10L).targetBuyPrice(1000).targetSellPrice(null).build();
        PriceRange range = new PriceRange(10L, 800, 1200);

        Integer result = evaluator.resolveReachedTargetPrice(watchlist, range);

        assertThat(result).isEqualTo(1000);
    }

    @Test
    @DisplayName("targetSellPrice만 설정돼 있고 range 안에 들어오면 targetSellPrice를 반환한다")
    void resolveReachedTargetPrice_onlyTargetSellPriceInRange_returnsTargetSellPrice() {
        Watchlist watchlist = Watchlist.builder()
                .userId(1L).cardId(10L).targetBuyPrice(null).targetSellPrice(1100).build();
        PriceRange range = new PriceRange(10L, 800, 1200);

        Integer result = evaluator.resolveReachedTargetPrice(watchlist, range);

        assertThat(result).isEqualTo(1100);
    }

    @Test
    @DisplayName("notifyIfNewlyReached(watchlist, null)은 알림 생성 권한 선점조차 시도하지 않고 조기 반환한다")
    void notifyIfNewlyReached_reachedTargetPriceNull_doesNotClaimOrNotify() {
        Watchlist watchlist = Watchlist.builder().userId(1L).cardId(10L).build();

        evaluator.notifyIfNewlyReached(watchlist, null);

        then(watchlistRepository).should(never()).markAsNotifiedIfNotYet(any());
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any(), any());
    }
}
