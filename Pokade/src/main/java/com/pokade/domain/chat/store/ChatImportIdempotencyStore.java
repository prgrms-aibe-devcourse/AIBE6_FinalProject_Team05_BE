package com.pokade.domain.chat.store;

public interface ChatImportIdempotencyStore {

    // key가 아직 마킹된 적 없으면 마킹하고 true, 이미 마킹돼 있으면(=이미 이관됨) false를 반환한다.
    boolean markIfAbsent(String key);
}
