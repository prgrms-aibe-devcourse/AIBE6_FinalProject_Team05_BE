package com.pokade.domain.chat.store;

public interface ChatImportIdempotencyStore {

    // key가 아직 마킹된 적 없으면 마킹하고 true, 이미 마킹돼 있으면(=이미 이관됨) false를 반환한다.
    boolean markIfAbsent(String key);

    // 마킹 이후 실제 처리(랭킹 조회/메시지 저장)가 실패했을 때 마킹을 되돌려, TTL이 끝나기 전에도
    // 재시도가 "이미 이관됨"으로 오판되지 않게 한다.
    void release(String key);
}
