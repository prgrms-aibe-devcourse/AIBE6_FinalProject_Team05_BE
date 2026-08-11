package com.pokade.domain.sync.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.pokade.domain.sync.client.dto.ScrydexCardPageResponse;

/**
 * 실물 카드(is_online_only:false)만 요청 단계에서 필터링하여 조회한다 - q 파라미터 자체로 Pocket 카드를
 * API 응답에서부터 제외해 불필요한 크레딧 소모를 줄인다. 저장 단계의 방어 코드는 {@code CardSyncService}에 있다.
 */
@Component
public class ScrydexApiClient implements ScrydexClient {

    private static final String CARDS_PATH = "/pokemon/v1/cards";
    private static final String ONLINE_ONLY_FILTER = "expansion.is_online_only:false";

    private final RestClient restClient;

    public ScrydexApiClient(ScrydexProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Api-Key", properties.apiKey())
                .defaultHeader("X-Team-ID", properties.teamId())
                .build();
    }

    @Override
    public ScrydexCardPageResponse fetchCardsPage(int page, int pageSize) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CARDS_PATH)
                        .queryParam("q", ONLINE_ONLY_FILTER)
                        .queryParam("include", "prices")
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .retrieve()
                .body(ScrydexCardPageResponse.class);
    }
}
