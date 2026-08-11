package com.pokade.domain.chat.store;

public interface ChatRateLimitStore {

    // 세션이 현재 잠금(쿨다운) 상태인지 확인
    boolean isLocked(String sessionId);

    // 이번 메시지가 직전 메시지와 같으면 연속 반복 횟수를 1 증가시키고, 다르면 1로 리셋한 뒤 그 값을 반환
    long recordAndCount(String sessionId, String message);

    // 반복 한도를 넘긴 세션을 일정 시간 잠금
    void lock(String sessionId);
}
