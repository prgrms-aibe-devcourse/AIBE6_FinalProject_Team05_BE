package com.pokade.domain.sync.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.pokade.domain.sync.entity.SyncLog;
import com.pokade.domain.sync.entity.type.SyncStatus;
import com.pokade.domain.sync.entity.type.SyncType;
import com.pokade.support.AbstractIntegrationTest;

@DataJpaTest
class SyncLogRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Test
    @DisplayName("t1 특정 동기화 타입이 성공으로 기록되어 있으면 존재 여부가 true다")
    void t1() {
        syncLogRepository.save(SyncLog.builder()
                .syncType(SyncType.EXPANSION)
                .status(SyncStatus.SUCCESS)
                .recordsSynced(8)
                .creditsUsed(0)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build());

        boolean exists = syncLogRepository.existsBySyncTypeAndStatus(SyncType.EXPANSION, SyncStatus.SUCCESS);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("t2 실패로만 기록된 타입은 SUCCESS 존재 여부가 false다")
    void t2() {
        syncLogRepository.save(SyncLog.builder()
                .syncType(SyncType.CARD_VARIANT)
                .status(SyncStatus.FAILED)
                .errorMessage("scrydex 응답 오류")
                .creditsUsed(0)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build());

        boolean exists = syncLogRepository.existsBySyncTypeAndStatus(SyncType.CARD_VARIANT, SyncStatus.SUCCESS);

        assertThat(exists).isFalse();
    }
}
