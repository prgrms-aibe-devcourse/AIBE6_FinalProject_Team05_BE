package com.pokade.domain.sync.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.sync.client.ScrydexClient;
import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.CardPriceDto;
import com.pokade.domain.sync.client.dto.CardVariantDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;
import com.pokade.domain.sync.entity.SyncLog;
import com.pokade.domain.sync.entity.type.SyncStatus;
import com.pokade.domain.sync.entity.type.SyncType;
import com.pokade.domain.sync.repository.SyncLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * expansions → cards → card_variants → card_prices 순서로 Scrydex 데이터를 동기화한다.
 * 각 단계의 성공/실패는 sync_logs에 기록되며, 이미 성공한 단계는 재실행 시 로그를 다시 남기지 않고
 * 실패했던 단계부터 이어서 진행한다(단, 목데이터 특성상 이전 단계 데이터는 매 호출마다 다시 조회한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final ScrydexClient scrydexClient;
    private final SyncLogRepository syncLogRepository;

    @Value("${sync.enabled:false}")
    private boolean syncEnabled;

    @Transactional
    public void sync() {
        if (!syncEnabled) {
            log.info("sync.enabled=false - 동기화를 실행하지 않습니다.");
            return;
        }

        List<ExpansionDto> expansions = runStep(SyncType.EXPANSION, scrydexClient::fetchExpansions);
        if (expansions == null) {
            return;
        }

        List<CardDto> cards = runStep(SyncType.CARD, () -> expansions.stream()
                .flatMap(e -> scrydexClient.fetchCards(e.id()).stream())
                .toList());
        if (cards == null) {
            return;
        }

        List<CardVariantDto> variants = runStep(SyncType.CARD_VARIANT, () -> cards.stream()
                .flatMap(c -> scrydexClient.fetchCardVariants(c.externalId()).stream())
                .toList());
        if (variants == null) {
            return;
        }

        runStep(SyncType.PRICE, () -> variants.stream()
                .flatMap(v -> scrydexClient.fetchCardPrices(v.variantId()).stream())
                .toList());
    }

    private <T> List<T> runStep(SyncType syncType, Supplier<List<T>> fetcher) {
        if (syncLogRepository.existsBySyncTypeAndStatus(syncType, SyncStatus.SUCCESS)) {
            return fetcher.get();
        }

        LocalDateTime startedAt = LocalDateTime.now();
        try {
            List<T> result = fetcher.get();
            syncLogRepository.save(SyncLog.builder()
                    .syncType(syncType)
                    .status(SyncStatus.SUCCESS)
                    .recordsSynced(result.size())
                    .creditsUsed(0)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .build());
            return result;
        } catch (RuntimeException e) {
            log.error("{} 단계 동기화 실패", syncType, e);
            syncLogRepository.save(SyncLog.builder()
                    .syncType(syncType)
                    .status(SyncStatus.FAILED)
                    .creditsUsed(0)
                    .errorMessage(e.getMessage())
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .build());
            return null;
        }
    }
}
