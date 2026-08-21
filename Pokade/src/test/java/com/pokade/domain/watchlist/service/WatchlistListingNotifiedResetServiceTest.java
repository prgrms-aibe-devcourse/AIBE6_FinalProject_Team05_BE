package com.pokade.domain.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.watchlist.entity.Watchlist;
import com.pokade.domain.watchlist.repository.WatchlistRepository;

@ExtendWith(MockitoExtension.class)
class WatchlistListingNotifiedResetServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

    @Mock
    private WatchlistListingNotifiedResetProcessor processor;

    @InjectMocks
    private WatchlistListingNotifiedResetService service;

    private Watchlist watchlist(Long id, Long cardId, Long variantId) {
        Watchlist watchlist = Watchlist.builder()
                .userId(id).cardId(cardId).variantId(variantId).targetBuyPrice(1000).build();
        ReflectionTestUtils.setField(watchlist, "id", id);
        return watchlist;
    }

    private record PrimaryVariantIdView(Long cardId, Long variantId) implements CardVariantRepository.PrimaryVariantIdView {
        public Long getCardId() { return cardId; }
        public Long getVariantId() { return variantId; }
    }

    @Test
    @DisplayName("리셋 후보가 없으면 카드/프로세서 조회를 하지 않는다")
    void resetListingNotifiedIfSoldOut_noCandidatesMeansNoop() {
        given(watchlistRepository.findByListingNotifiedTrue()).willReturn(List.of());

        service.resetListingNotifiedIfSoldOut();

        then(cardVariantRepository).should(never()).findPrimaryVariantIdsByCardIds(any());
        then(processor).should(never()).process(any(), any());
    }

    @Test
    @DisplayName("variantId가 명시된 워치리스트는 그 값을 그대로 프로세서에 전달한다")
    void resetListingNotifiedIfSoldOut_passesExplicitVariantIdThrough() {
        Watchlist explicit = watchlist(10L, 1L, 100L);
        given(watchlistRepository.findByListingNotifiedTrue()).willReturn(List.of(explicit));
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L))).willReturn(List.of());

        service.resetListingNotifiedIfSoldOut();

        then(processor).should().process(10L, 100L);
    }

    @Test
    @DisplayName("variantId가 null인 워치리스트는 카드의 대표 variant ID로 치환해서 프로세서에 전달한다")
    void resetListingNotifiedIfSoldOut_resolvesNullVariantIdToPrimary() {
        Watchlist watchingPrimary = watchlist(10L, 1L, null);
        given(watchlistRepository.findByListingNotifiedTrue()).willReturn(List.of(watchingPrimary));
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L)))
                .willReturn(List.of(new PrimaryVariantIdView(1L, 999L)));

        service.resetListingNotifiedIfSoldOut();

        then(processor).should().process(10L, 999L);
    }

    @Test
    @DisplayName("대표 variant 정보가 없으면 null을 그대로 프로세서에 전달한다")
    void resetListingNotifiedIfSoldOut_passesNullWhenNoPrimaryVariantInfo() {
        Watchlist watchingPrimary = watchlist(10L, 1L, null);
        given(watchlistRepository.findByListingNotifiedTrue()).willReturn(List.of(watchingPrimary));
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L))).willReturn(List.of());

        service.resetListingNotifiedIfSoldOut();

        then(processor).should().process(10L, null);
    }

    @Test
    @DisplayName("한 건의 프로세서 처리 중 예외가 발생해도 다른 건은 계속 처리된다")
    void resetListingNotifiedIfSoldOut_continuesAfterOneItemFails() {
        Watchlist w1 = watchlist(10L, 1L, null);
        Watchlist w2 = watchlist(20L, 2L, null);
        given(watchlistRepository.findByListingNotifiedTrue()).willReturn(List.of(w1, w2));
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(List.of(1L, 2L))).willReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(processor).process(10L, null);

        service.resetListingNotifiedIfSoldOut();

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        then(processor).should(times(2)).process(idCaptor.capture(), any());
        assertThat(idCaptor.getAllValues()).containsExactly(10L, 20L);
    }
}
