package com.pokade.domain.watchlist.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.price.dto.CardPriceSummaryResponse;
import com.pokade.domain.price.repository.PriceTradeStatsRepository;
import com.pokade.domain.price.service.PriceService;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.dto.WatchlistUpdateRequest;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock PriceService priceService;
    @Mock CardRepository cardRepository;
    @Mock PriceTradeStatsRepository priceTradeStatsRepository;
    @Mock CardNameKoResolver cardNameKoResolver;
    @Mock NotificationService notificationService;
    WatchlistService watchlistService;

    // 목표가 판정/알림 로직은 WatchlistTargetPriceEvaluator로 분리돼 있지만, 그 판정 결과가 WatchlistService의
    // 응답(targetReached/isNotified)에 실제로 반영되는지는 이 테스트가 여전히 검증해야 하므로 mock이 아니라
    // 같은 mock 협력자로 구성한 실제 인스턴스를 주입한다.
    @BeforeEach
    void setUp() {
        WatchlistTargetPriceEvaluator watchlistTargetPriceEvaluator =
                new WatchlistTargetPriceEvaluator(watchlistRepository, cardRepository, notificationService, cardNameKoResolver);
        watchlistService = new WatchlistService(
                watchlistRepository, priceService, cardRepository, priceTradeStatsRepository, cardNameKoResolver, watchlistTargetPriceEvaluator);
    }

    private Watchlist watchlist(Long userId, Long cardId) {
        return Watchlist.builder()
                .userId(userId).cardId(cardId).variantId(null)
                .targetBuyPrice(1000).targetSellPrice(null)
                .build();
    }

    private record PriceRange(Long cardId, Integer minPrice, Integer maxPrice)
            implements PriceTradeStatsRepository.CardPriceRangeView {
        public Long getCardId() { return cardId; }
        public Integer getMinPrice() { return minPrice; }
        public Integer getMaxPrice() { return maxPrice; }
    }

    // ===== 등록 =====
    @Test
    @DisplayName("등록: 목표가 둘 다 null이면 TARGET_PRICE_REQUIRED")
    void addWatchlist_targetPriceRequired() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, null, null);

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TARGET_PRICE_REQUIRED);
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 이미 등록된 카드면 DUPLICATE_WATCHLIST")
    void addWatchlist_duplicate() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(true);

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_WATCHLIST);
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 20개 이미 등록했으면 WATCHLIST_LIMIT_EXCEEDED")
    void addWatchlist_limitExceeded() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(20L);

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("등록: 검증 통과하면 저장 후 WatchlistResponse 반환")
    void addWatchlist_success() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, 2L, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        then(watchlistRepository).should().acquireUserLock(1L);
        then(watchlistRepository).should().save(any(Watchlist.class));
        assertThat(response.cardId()).isEqualTo(1L);
        assertThat(response.variantId()).isEqualTo(2L);
        assertThat(response.targetBuyPrice()).isEqualTo(1000);
    }

    @Test
    @DisplayName("등록: 등록 시점에 이미 체결가가 목표가 구간 안이면 targetReached=true와 isNotified=true가 함께 반영된다")
    void addWatchlist_targetAlreadyReached_marksAsNotified() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 2000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(List.of(1L), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(1L, 1800, 2200)));
        Card card = Card.builder().id(1L).name("리자몽").build();
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        assertThat(response.targetReached()).isTrue();
        assertThat(response.isNotified()).isTrue();
        then(notificationService).should().createPriceTargetNotification(any(Watchlist.class), eq("리자몽"), eq(2000));
    }

    @Test
    @DisplayName("등록: 체결가가 목표가 구간 밖이면 targetReached/isNotified 모두 false로 유지된다")
    void addWatchlist_targetNotReached_keepsNotifiedFalse() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 9000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(priceTradeStatsRepository.findPriceRangesByCardIds(List.of(1L), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(1L, 1800, 2200)));

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        assertThat(response.targetReached()).isFalse();
        assertThat(response.isNotified()).isFalse();
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
    }

    @Test
    @DisplayName("등록: 목표가 도달했지만 카드를 찾지 못하면 알림 생성은 스킵되고 targetReached/isNotified는 정상 반영된다")
    void addWatchlist_targetReachedButCardNotFound_skipsNotification() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 2000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);
        given(priceTradeStatsRepository.findPriceRangesByCardIds(List.of(1L), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(1L, 1800, 2200)));
        given(cardRepository.findById(1L)).willReturn(Optional.empty());

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        assertThat(response.targetReached()).isTrue();
        assertThat(response.isNotified()).isTrue();
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
    }

    @Test
    @DisplayName("등록: 사전 체크를 통과했지만 저장 시점에 DB UNIQUE 제약을 위반하면(동시 등록 경합) DUPLICATE_WATCHLIST로 변환된다")
    void addWatchlist_saveThrowsDataIntegrityViolation_convertsToDuplicateWatchlist() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 1000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class)))
                .willThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> watchlistService.addWatchlist(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_WATCHLIST);
    }

    @Test
    @DisplayName("등록: variantId가 null이어도 정상 등록된다")
    void addWatchlist_success_variantIdNull() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, 10000, null);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        assertThat(response.variantId()).isNull();
        assertThat(response.targetBuyPrice()).isEqualTo(10000);
    }

    @Test
    @DisplayName("등록: targetSellPrice만 있어도 정상 등록된다")
    void addWatchlist_success_targetSellPriceOnly() {
        WatchlistCreateRequest request = new WatchlistCreateRequest(1L, null, null, 5000);
        given(watchlistRepository.existsByUserIdAndCardId(1L, 1L)).willReturn(false);
        given(watchlistRepository.countByUserId(1L)).willReturn(0L);
        given(watchlistRepository.save(any(Watchlist.class))).willAnswer(invocation -> invocation.getArgument(0));

        WatchlistResponse response = watchlistService.addWatchlist(1L, request);

        assertThat(response.targetBuyPrice()).isNull();
        assertThat(response.targetSellPrice()).isEqualTo(5000);
    }

    // ===== 목록 조회 =====
    @Test
    @DisplayName("목록 조회: 등록된 워치리스트 없으면 빈 리스트")
    void getWatchlist_empty() {
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of());

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result).isEmpty();
        then(priceService).should(never()).getSummaries(any(), any(), anyBoolean());
        then(cardRepository).should(never()).findAllById(any());
        then(priceTradeStatsRepository).should(never()).findPriceRangesByCardIdsSincePerCard(any(), any(), any(), any());
        then(priceService).should(never()).getChangeRates(any());
    }

    @Test
    @DisplayName("목록 조회: 등록된 워치리스트 개수만큼 반환하고, cardId로 시세를 배치 조회해 매핑한다")
    void getWatchlist_success() {
        given(watchlistRepository.findByUserId(1L))
                .willReturn(List.of(watchlist(1L, 10L), watchlist(1L, 20L)));
        given(priceService.getSummaries(eq(List.of(10L, 20L)), isNull(), eq(true)))
                .willReturn(List.of(
                        new CardPriceSummaryResponse(10L, 900, 800, null, "KRW", null, null),
                        new CardPriceSummaryResponse(20L, null, null, null, "KRW", null, null)
                ));

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).currentPrice().buyPrice()).isEqualTo(900);
        assertThat(result.get(1).currentPrice().buyPrice()).isNull();
    }

    @Test
    @DisplayName("목록 조회: 배치로 조회한 카드 정보(이름·세트명·이미지)가 함께 매핑된다")
    void getWatchlist_includesCardInfo() {
        Watchlist watchlist = watchlist(1L, 10L);
        Card card = Card.builder()
                .id(10L).name("피카츄").setName("기본팩").imageSmall("small.png").imageMedium("medium.png")
                .build();
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true))).willReturn(List.of());
        given(cardRepository.findAllById(List.of(10L))).willReturn(List.of(card));
        given(cardNameKoResolver.resolve(card)).willReturn("피카츄");

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).cardName()).isEqualTo("피카츄");
        assertThat(result.get(0).cardNameKo()).isEqualTo("피카츄");
        assertThat(result.get(0).setName()).isEqualTo("기본팩");
        assertThat(result.get(0).imageUrl()).isEqualTo("medium.png");
    }

    @Test
    @DisplayName("목록 조회: 카드 정보를 찾지 못하면(삭제된 카드 등) cardNameKo는 resolver를 호출하지 않고 null이다")
    void getWatchlist_cardNotFound_cardNameKoNull() {
        Watchlist watchlist = watchlist(1L, 10L);
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true))).willReturn(List.of());
        given(cardRepository.findAllById(List.of(10L))).willReturn(List.of());

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).cardNameKo()).isNull();
        then(cardNameKoResolver).should(never()).resolve(any(Card.class));
    }

    // targetReached는 "지금 시세가 목표가보다 높은지/낮은지"가 아니라 "체결가가 그동안 오르내리며
    // 목표가를 한 번이라도 지나간 적이 있는지"로 판정한다(사용자 요청, 2026-08-13) - 그래서 아래
    // 테스트들은 currentPrice가 아니라 findPriceRangesByCardIds가 돌려주는 최저~최고 구간을 기준으로 한다.
    @Test
    @DisplayName("목록 조회: 목표가가 체결가 최저~최고 구간 안에 있으면 targetReached=true")
    void getWatchlist_targetWithinRange() {
        Watchlist watchlist = watchlist(1L, 10L); // targetBuyPrice = 1000
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true))).willReturn(List.of());
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(eq(List.of(10L)), eq(List.of(LocalDateTime.MIN)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(new PriceRange(10L, 800, 1200)));

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).targetReached()).isTrue();
    }

    @Test
    @DisplayName("목록 조회: 목표가가 현재가보다 훨씬 높아도, 체결가 최고가가 그 목표가에 닿았으면 targetReached=true")
    void getWatchlist_targetAboveCurrentButWithinHistoricalHigh() {
        Watchlist watchlist = Watchlist.builder()
                .userId(1L).cardId(10L).targetBuyPrice(9000).build(); // 현재가(가정 500)보다 훨씬 높은 목표가
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true)))
                .willReturn(List.of(new CardPriceSummaryResponse(10L, 500, null, null, "KRW", null, null)));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(eq(List.of(10L)), eq(List.of(LocalDateTime.MIN)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(new PriceRange(10L, 300, 9500))); // 과거 한때 9500까지 거래된 적 있음

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).targetReached()).isTrue();
    }

    @Test
    @DisplayName("목록 조회: 목표가가 체결가 최저~최고 구간 밖이면 targetReached=false")
    void getWatchlist_targetOutsideRange() {
        Watchlist watchlist = watchlist(1L, 10L); // targetBuyPrice = 1000
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true))).willReturn(List.of());
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(eq(List.of(10L)), eq(List.of(LocalDateTime.MIN)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(new PriceRange(10L, 1500, 2000)));

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).targetReached()).isFalse();
    }

    @Test
    @DisplayName("목록 조회: 체결 이력이 없는 카드는 targetReached=false")
    void getWatchlist_noTradeHistory_notReached() {
        Watchlist watchlist = watchlist(1L, 10L);
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true))).willReturn(List.of());
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(eq(List.of(10L)), eq(List.of(LocalDateTime.MIN)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of());

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).targetReached()).isFalse();
    }

    // #275: 워치리스트마다 서로 다른 등록(createdAt) 시점이 카드ID와 정확히 같은 순서로 페어링되어
    // 리포지토리에 전달되는지 검증한다(배치 페어링 파라미터라 순서가 어긋나면 다른 카드의 since를
    // 잘못 적용하게 됨).
    @Test
    @DisplayName("목록 조회: 워치리스트마다 다른 등록 시점(createdAt)이 카드ID와 정확히 페어링되어 전달된다(#275)")
    void getWatchlist_pairsEachCardWithItsOwnCreatedAt() {
        Watchlist watchlistA = watchlist(1L, 10L);
        Watchlist watchlistB = watchlist(1L, 20L);
        LocalDateTime createdAtA = LocalDateTime.now().minusDays(10);
        LocalDateTime createdAtB = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(watchlistA, "createdAt", createdAtA);
        ReflectionTestUtils.setField(watchlistB, "createdAt", createdAtB);
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlistA, watchlistB));
        given(priceService.getSummaries(eq(List.of(10L, 20L)), isNull(), eq(true))).willReturn(List.of());
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(
                eq(List.of(10L, 20L)), eq(List.of(createdAtA, createdAtB)), isNull(), eq(TradeStatus.COMPLETED)))
                .willReturn(List.of(new PriceRange(10L, 800, 1200)));

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).targetReached()).isTrue();
        assertThat(result.get(1).targetReached()).isFalse();
    }

    @Test
    @DisplayName("목록 조회: PriceService.getChangeRates() 배치 조회 결과가 changeRate로 매핑된다")
    void getWatchlist_includesChangeRate() {
        Watchlist watchlist = watchlist(1L, 10L);
        given(watchlistRepository.findByUserId(1L)).willReturn(List.of(watchlist));
        given(priceService.getSummaries(eq(List.of(10L)), isNull(), eq(true))).willReturn(List.of());
        given(priceService.getChangeRates(List.of(10L)))
                .willReturn(java.util.Map.of(10L, new java.math.BigDecimal("3.25")));

        List<WatchlistResponse> result = watchlistService.getWatchlist(1L);

        assertThat(result.get(0).changeRate()).isEqualTo(new java.math.BigDecimal("3.25"));
    }

    // ===== 목표가 수정 =====
    @Test
    @DisplayName("수정: 목표가 둘 다 null이면 TARGET_PRICE_REQUIRED")
    void updateWatchlist_targetPriceRequired() {
        WatchlistUpdateRequest request = new WatchlistUpdateRequest(null, null, null);

        assertThatThrownBy(() -> watchlistService.updateWatchlist(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TARGET_PRICE_REQUIRED);
        then(watchlistRepository).should(never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("수정: 본인 소유가 아니거나 존재하지 않으면 WATCHLIST_NOT_FOUND")
    void updateWatchlist_notFound() {
        WatchlistUpdateRequest request = new WatchlistUpdateRequest(2000, null, null);
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.updateWatchlist(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WATCHLIST_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 목표가가 실제로 바뀌면 isNotified가 false로 리셋된다")
    void updateWatchlist_changedPrice_resetsIsNotified() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        target.markAsNotified();
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetBuyPrice()).isEqualTo(2000);
        assertThat(response.isNotified()).isFalse();
    }

    @Test
    @DisplayName("수정: 기존과 동일한 값으로 수정하면(no-op) isNotified는 유지된다")
    void updateWatchlist_samePrice_keepsIsNotified() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        target.markAsNotified();
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(1000, null, null));

        assertThat(response.isNotified()).isTrue();
    }

    @Test
    @DisplayName("수정: 한쪽 목표가만 보내면 다른 쪽 기존 값은 유지된다(부분 업데이트)")
    void updateWatchlist_partialUpdate_keepsOtherPrice() {
        Watchlist target = Watchlist.builder()
                .userId(1L).cardId(10L).variantId(null)
                .targetBuyPrice(1000).targetSellPrice(5000)
                .build();
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetBuyPrice()).isEqualTo(2000);
        assertThat(response.targetSellPrice()).isEqualTo(5000);
    }

    @Test
    @DisplayName("수정: resendNotification=true면 가격 없이도 통과하고 isNotified가 false로 리셋된다")
    void updateWatchlist_resendNotification_resetsIsNotifiedWithoutPrice() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        target.markAsNotified();
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(null, null, true));

        assertThat(response.targetBuyPrice()).isEqualTo(1000);
        assertThat(response.isNotified()).isFalse();
    }

    @Test
    @DisplayName("수정: resendNotification=true 요청은 이미 isNotified=false여도 에러 없이 성공한다(멱등)")
    void updateWatchlist_resendNotification_idempotentWhenAlreadyFalse() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000, isNotified = false
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(null, null, true));

        assertThat(response.isNotified()).isFalse();
    }

    @Test
    @DisplayName("수정: 변경된 목표가가 체결가 구간 안에 있으면 targetReached=true가 실제로 반영된다")
    void updateWatchlist_targetReached_reflectsActualPriceRange() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 1800, 2200)));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetBuyPrice()).isEqualTo(2000);
        assertThat(response.targetReached()).isTrue();
    }

    @Test
    @DisplayName("수정: 변경된 목표가가 체결가 구간 밖이면 targetReached=false로 유지된다")
    void updateWatchlist_targetReached_falseWhenOutOfRange() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 1800, 2200)));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(9000, null, null));

        assertThat(response.targetReached()).isFalse();
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
    }

    @Test
    @DisplayName("수정: 배치가 그 사이 먼저 선점했으면(claimed=0) 도달 상태여도 중복 알림을 만들지 않는다")
    void updateWatchlist_batchAlreadyClaimed_skipsDuplicateNotification() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000, isNotified = false(메모리 스냅샷 - 배치가 이미 선점한 상황을 재현)
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(0);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 1800, 2200)));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetReached()).isTrue();
        assertThat(response.isNotified()).isFalse();
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
    }

    @Test
    @DisplayName("수정: 미도달 상태에서 가격 변경으로 새로 도달하면 isNotified=true가 되고 실제 알림이 생성된다")
    void updateWatchlist_newlyReachedByPriceChange_marksNotifiedAndNotifies() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000, isNotified = false
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 1800, 2200)));
        Card card = Card.builder().id(10L).name("리자몽").build();
        given(cardRepository.findById(10L)).willReturn(Optional.of(card));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetReached()).isTrue();
        assertThat(response.isNotified()).isTrue();
        then(notificationService).should().createPriceTargetNotification(any(Watchlist.class), eq("리자몽"), eq(2000));
    }

    @Test
    @DisplayName("수정: 이미 알림이 간 상태에서 가격 변경 없이(no-op) 수정하면 도달해 있어도 다시 알리지 않는다")
    void updateWatchlist_alreadyNotified_samePrice_doesNotReNotify() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        target.markAsNotified();
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 800, 1200)));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(1000, null, null));

        assertThat(response.targetReached()).isTrue();
        assertThat(response.isNotified()).isTrue();
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
    }

    @Test
    @DisplayName("수정: 이미 도달 범위 안인 상태에서 재알림(resend) 요청만 오면 즉시 다시 알림이 생성된다")
    void updateWatchlist_resendWhenAlreadyInRange_notifiesImmediately() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        target.markAsNotified();
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 800, 1200)));
        Card card = Card.builder().id(10L).name("리자몽").build();
        given(cardRepository.findById(10L)).willReturn(Optional.of(card));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(null, null, true));

        assertThat(response.isNotified()).isTrue();
        then(notificationService).should().createPriceTargetNotification(any(Watchlist.class), eq("리자몽"), eq(1000));
    }

    @Test
    @DisplayName("수정: resend와 가격변경(신규 도달)이 동시에 와도 알림은 정확히 한 번만 생성된다")
    void updateWatchlist_resendAndPriceChangeBothReached_notifiesOnce() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000, isNotified = false
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 1800, 2200)));
        Card card = Card.builder().id(10L).name("리자몽").build();
        given(cardRepository.findById(10L)).willReturn(Optional.of(card));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, true));

        assertThat(response.isNotified()).isTrue();
        then(notificationService).should(times(1)).createPriceTargetNotification(any(Watchlist.class), eq("리자몽"), eq(2000));
    }

    @Test
    @DisplayName("수정: 목표가 도달했지만 카드를 찾지 못하면 알림 생성은 스킵되고 targetReached/isNotified는 정상 반영된다")
    void updateWatchlist_targetReachedButCardNotFound_skipsNotification() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000, isNotified = false
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(watchlistRepository.markAsNotifiedIfNotYet(any())).willReturn(1);
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 1800, 2200)));
        given(cardRepository.findById(10L)).willReturn(Optional.empty());

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetReached()).isTrue();
        assertThat(response.isNotified()).isTrue();
        then(notificationService).should(never()).createPriceTargetNotification(any(), any(), any());
    }

    // #275 버그 재현 테스트: 워치리스트 등록(createdAt) 훨씬 이전에 목표가 범위였다가 지금은 벗어난
    // 카드에 대해, 등록 시점 이후 범위만 봤을 때는 미도달이어야 한다는 걸 실제 createdAt 값으로 검증한다.
    // (수정 전 버그: 전체 기간 range를 써서 등록 이전 이력만으로도 "도달"로 잘못 판정됐음)
    @Test
    @DisplayName("수정: 등록(createdAt) 시점 이후 범위만 판정에 반영되고, 그 정확한 시점이 리포지토리에 전달된다(#275)")
    void updateWatchlist_usesActualCreatedAtAsRangeScope() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000
        LocalDateTime registeredAt = LocalDateTime.now().minusDays(3);
        ReflectionTestUtils.setField(target, "createdAt", registeredAt);
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        // 등록 이후 범위로 좁히면 목표가(2000)가 그 구간(2500~2700) 밖이라 미도달이어야 한다 -
        // 등록 이전 전체 기간을 봤다면(버그 재현 시나리오) 과거 저가 이력 때문에 도달로 잘못 나왔을 수 있다.
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(
                List.of(10L), List.of(registeredAt), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 2500, 2700)));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(2000, null, null));

        assertThat(response.targetReached()).isFalse();
        // 옛 방식(전체 기간 findPriceRangesByCardIds)은 더 이상 호출되지 않아야 한다.
        then(priceTradeStatsRepository).should(never()).findPriceRangesByCardIds(any(), any(), any());
    }

    @Test
    @DisplayName("수정: createdAt이 null(방어적 케이스)이어도 NPE 없이 LocalDateTime.MIN으로 폴백해서 정상 동작한다(#275)")
    void updateWatchlist_nullCreatedAt_fallsBackToMinSafely() {
        Watchlist target = watchlist(1L, 10L); // targetBuyPrice = 1000, createdAt = null(빌더 기본값)
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));
        given(priceTradeStatsRepository.findPriceRangesByCardIdsSincePerCard(
                List.of(10L), List.of(LocalDateTime.MIN), null, TradeStatus.COMPLETED))
                .willReturn(List.of(new PriceRange(10L, 800, 1200)));

        WatchlistResponse response = watchlistService.updateWatchlist(1L, 1L, new WatchlistUpdateRequest(1000, null, null));

        assertThat(response.targetReached()).isTrue();
    }

    // ===== 삭제 =====
    @Test
    @DisplayName("삭제: 존재하지 않으면 WATCHLIST_NOT_FOUND")
    void deleteWatchlist_notFound() {
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.deleteWatchlist(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WATCHLIST_NOT_FOUND);
        then(watchlistRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 정상 케이스면 repository.delete() 호출")
    void deleteWatchlist_success() {
        Watchlist target = watchlist(1L, 10L);
        given(watchlistRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(target));

        watchlistService.deleteWatchlist(1L, 1L);

        then(watchlistRepository).should().delete(target);
    }
}
