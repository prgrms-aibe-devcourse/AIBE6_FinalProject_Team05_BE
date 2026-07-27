package com.pokade.domain.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.pokade.domain.sync.client.ScrydexClient;
import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.CardPriceDto;
import com.pokade.domain.sync.client.dto.CardVariantDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;
import com.pokade.domain.sync.entity.SyncLog;
import com.pokade.domain.sync.entity.type.SyncStatus;
import com.pokade.domain.sync.entity.type.SyncType;
import com.pokade.domain.sync.repository.SyncLogRepository;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private ScrydexClient scrydexClient;

    @Mock
    private SyncLogRepository syncLogRepository;

    @InjectMocks
    private SyncService syncService;

    @Captor
    private ArgumentCaptor<SyncLog> syncLogCaptor;

    private ExpansionDto expansion;
    private CardDto card;
    private CardVariantDto variant;
    private CardPriceDto price;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(syncService, "syncEnabled", true);

        expansion = new ExpansionDto("base1", "Base", "Base", null, 102, "EN", null);
        card = new CardDto("base1-4", "Charizard", "Base", "Rare Holo", "Pokémon",
                List.of("Fire"), "4/102", "base1");
        variant = new CardVariantDto("base1-4-unlimitedHolofoil", "base1-4", "unlimitedHolofoil", true);
        price = new CardPriceDto("base1-4-unlimitedHolofoil", "graded", "10", "PSA",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "USD");
    }

    @Test
    @DisplayName("t1 sync.enabled=false이면 아무 단계도 실행하지 않는다")
    void t1() {
        ReflectionTestUtils.setField(syncService, "syncEnabled", false);

        syncService.sync();

        verify(scrydexClient, never()).fetchExpansions();
        verify(syncLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("t2 4단계 모두 성공하면 각 단계마다 SUCCESS 로그를 순서대로 기록한다")
    void t2() {
        given(scrydexClient.fetchExpansions()).willReturn(List.of(expansion));
        given(scrydexClient.fetchCards("base1")).willReturn(List.of(card));
        given(scrydexClient.fetchCardVariants("base1-4")).willReturn(List.of(variant));
        given(scrydexClient.fetchCardPrices("base1-4-unlimitedHolofoil")).willReturn(List.of(price));

        syncService.sync();

        verify(syncLogRepository, times(4)).save(syncLogCaptor.capture());
        List<SyncLog> logs = syncLogCaptor.getAllValues();
        assertThat(logs).extracting(SyncLog::getSyncType)
                .containsExactly(SyncType.EXPANSION, SyncType.CARD, SyncType.CARD_VARIANT, SyncType.PRICE);
        assertThat(logs).allMatch(l -> l.getStatus() == SyncStatus.SUCCESS);
        assertThat(logs).extracting(SyncLog::getRecordsSynced).containsExactly(1, 1, 1, 1);
    }

    @Test
    @DisplayName("t3 중간 단계(CARD_VARIANT)에서 실패하면 실패 기록을 남기고 이후 단계(PRICE)는 진행하지 않는다")
    void t3() {
        given(scrydexClient.fetchExpansions()).willReturn(List.of(expansion));
        given(scrydexClient.fetchCards("base1")).willReturn(List.of(card));
        given(scrydexClient.fetchCardVariants("base1-4")).willThrow(new RuntimeException("scrydex 응답 오류"));

        syncService.sync();

        verify(syncLogRepository, times(3)).save(syncLogCaptor.capture());
        List<SyncLog> logs = syncLogCaptor.getAllValues();
        assertThat(logs).extracting(SyncLog::getSyncType)
                .containsExactly(SyncType.EXPANSION, SyncType.CARD, SyncType.CARD_VARIANT);
        assertThat(logs.get(2).getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(logs.get(2).getErrorMessage()).contains("scrydex 응답 오류");
        verify(scrydexClient, never()).fetchCardPrices(any());
    }

    @Test
    @DisplayName("t4 이미 성공한 단계는 재실행 시 로그를 다시 남기지 않고 다음 단계부터 재개한다")
    void t4() {
        given(syncLogRepository.existsBySyncTypeAndStatus(SyncType.EXPANSION, SyncStatus.SUCCESS)).willReturn(true);
        given(syncLogRepository.existsBySyncTypeAndStatus(SyncType.CARD, SyncStatus.SUCCESS)).willReturn(true);
        given(scrydexClient.fetchExpansions()).willReturn(List.of(expansion));
        given(scrydexClient.fetchCards("base1")).willReturn(List.of(card));
        given(scrydexClient.fetchCardVariants("base1-4")).willReturn(List.of(variant));
        given(scrydexClient.fetchCardPrices("base1-4-unlimitedHolofoil")).willReturn(List.of(price));

        syncService.sync();

        verify(scrydexClient).fetchExpansions();
        verify(scrydexClient).fetchCards("base1");
        verify(syncLogRepository, times(2)).save(syncLogCaptor.capture());
        List<SyncLog> logs = syncLogCaptor.getAllValues();
        assertThat(logs).extracting(SyncLog::getSyncType)
                .containsExactly(SyncType.CARD_VARIANT, SyncType.PRICE);
        assertThat(logs).allMatch(l -> l.getStatus() == SyncStatus.SUCCESS);
    }
}
