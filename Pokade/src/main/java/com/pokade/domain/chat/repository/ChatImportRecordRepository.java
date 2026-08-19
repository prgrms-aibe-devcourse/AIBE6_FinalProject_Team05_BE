package com.pokade.domain.chat.repository;

import com.pokade.domain.chat.entity.ChatImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatImportRecordRepository extends JpaRepository<ChatImportRecord, Long> {
}
