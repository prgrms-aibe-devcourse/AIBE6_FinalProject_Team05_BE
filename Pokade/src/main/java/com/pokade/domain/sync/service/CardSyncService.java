package com.pokade.domain.sync.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.pokade.domain.sync.client.ScrydexClient;
import com.pokade.domain.sync.client.ScrydexProperties;
import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.ScrydexCardPageResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scrydex 카드 목록 API를 page=1부터 total_count에 도달할 때까지 순회하며, 카드 1건마다
 * {@link CardUpsertService#upsertCard}로 expansions/cards/card_variants/card_prices를 upsert한다.
 * 카드 하나가 파싱/저장에 실패해도 여기서 예외를 잡아 로그만 남기고 나머지 배치는 계속 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardSyncService {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final ScrydexClient scrydexClient;
    private final CardUpsertService cardUpsertService;
    private final ScrydexProperties scrydexProperties;

    // 관리자 트리거 엔드포인트가 중복 실행을 막는 데만 쓰는 플래그 - 배치 자체의 동시성 제어는 아님
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 관리자 엔드포인트에서 호출하는 진입점 - HTTP 응답을 안 기다리게 백그라운드 스레드에서 전체 동기화를
     * 돌린다. {@link org.springframework.scheduling.annotation.Async}는 스프링 프록시를 통해서만 적용되므로
     * 같은 빈 안에서 this.syncAll()을 부르면 안 되고, 반드시 다른 빈(컨트롤러)이 이 메서드를 호출해야 한다.
     */
    @Async
    public void syncAllAsync() {
        if (!running.compareAndSet(false, true)) {
            log.warn("이미 동기화가 진행 중이라 이번 트리거는 무시합니다.");
            return;
        }
        try {
            syncAll();
        } finally {
            running.set(false);
        }
    }

    /** 설정된 page-size(기본 100)로 전체 카드를 끝까지 동기화한다. */
    public SyncSummary syncAll() {
        return syncAll(resolvePageSize());
    }

    public SyncSummary syncAll(int pageSize) {
        SyncSummary summary = new SyncSummary();

        ScrydexCardPageResponse firstPage = scrydexClient.fetchCardsPage(1, pageSize);
        int totalCount = firstPage.totalCount() == null ? 0 : firstPage.totalCount();
        summary.setTotalCount(totalCount);
        processPage(firstPage, summary);

        int totalPages = (totalCount + pageSize - 1) / pageSize;
        for (int page = 2; page <= totalPages; page++) {
            ScrydexCardPageResponse pageResponse = scrydexClient.fetchCardsPage(page, pageSize);
            processPage(pageResponse, summary);
            log.info("Scrydex 동기화 진행 - {}/{} 페이지 ({}건 중 처리 {}건, 스킵 {}건, 실패 {}건)",
                    page, totalPages, totalCount, summary.getProcessed(), summary.getSkipped(), summary.getFailed());
        }

        log.info("Scrydex 동기화 완료 - 전체 {}건 중 처리 {}건, 스킵 {}건, 실패 {}건",
                totalCount, summary.getProcessed(), summary.getSkipped(), summary.getFailed());
        return summary;
    }

    /** 검증용 - 정해진 한 페이지만 가져와 동기화한다(예: pageSize=10, page=1로 소량 검증). */
    public SyncSummary syncPage(int page, int pageSize) {
        SyncSummary summary = new SyncSummary();
        ScrydexCardPageResponse pageResponse = scrydexClient.fetchCardsPage(page, pageSize);
        summary.setTotalCount(pageResponse.totalCount() == null ? 0 : pageResponse.totalCount());
        processPage(pageResponse, summary);
        return summary;
    }

    private void processPage(ScrydexCardPageResponse pageResponse, SyncSummary summary) {
        List<CardDto> cards = pageResponse == null ? null : pageResponse.data();
        if (cards == null) {
            return;
        }
        for (CardDto card : cards) {
            try {
                boolean saved = cardUpsertService.upsertCard(card);
                if (saved) {
                    summary.addProcessed();
                } else {
                    summary.addSkipped();
                }
            } catch (RuntimeException e) {
                summary.addFailed();
                log.error("카드 동기화 실패 - externalId={}", card.id(), e);
            }
        }
    }

    private int resolvePageSize() {
        return scrydexProperties.pageSize() > 0 ? scrydexProperties.pageSize() : DEFAULT_PAGE_SIZE;
    }
}
