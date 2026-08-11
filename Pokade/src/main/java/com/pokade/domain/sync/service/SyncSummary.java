package com.pokade.domain.sync.service;

/** Scrydex 동기화 배치 1회 실행의 처리 결과 집계. */
public class SyncSummary {

    private int processed;
    private int skipped;
    private int failed;
    private int totalCount;

    void addProcessed() {
        processed++;
    }

    void addSkipped() {
        skipped++;
    }

    void addFailed() {
        failed++;
    }

    void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getProcessed() {
        return processed;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getFailed() {
        return failed;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
