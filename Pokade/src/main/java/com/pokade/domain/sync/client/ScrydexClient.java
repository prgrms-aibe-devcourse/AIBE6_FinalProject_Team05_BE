package com.pokade.domain.sync.client;

import com.pokade.domain.sync.client.dto.ScrydexCardPageResponse;

public interface ScrydexClient {

    /**
     * GET /pokemon/v1/cards?q=expansion.is_online_only:false&include=prices&page={page}&pageSize={pageSize}
     * 실물 카드(is_online_only:false)만 요청 단계에서 필터링된 한 페이지를 반환한다.
     */
    ScrydexCardPageResponse fetchCardsPage(int page, int pageSize);
}
