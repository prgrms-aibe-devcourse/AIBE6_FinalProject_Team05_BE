package com.pokade.domain.chat.repository;

import com.pokade.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 이력 조회: sessionId + userId 둘 다 일치해야 조회됨(본인 세션만 접근 가능)
    Page<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, Long userId, Pageable pageable);
}
