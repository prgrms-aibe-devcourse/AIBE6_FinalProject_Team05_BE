package com.pokade.domain.sync.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokade.domain.sync.entity.SyncLog;
import com.pokade.domain.sync.entity.type.SyncStatus;
import com.pokade.domain.sync.entity.type.SyncType;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    boolean existsBySyncTypeAndStatus(SyncType syncType, SyncStatus status);
}
