package com.pokade.domain.sync.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokade.domain.sync.service.CardSyncService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * Scrydex 전체 카드 동기화를 관리자가 수동으로 트리거하는 엔드포인트. ADMIN 권한만 호출 가능
 * (SecurityConfig의 {@code /api/admin/**} 규칙). 21184건 전체를 순회하는 배치라 응답을 기다리지 않고
 * 백그라운드로 실행하며, 진행 상황/완료 여부는 서버 로그(CardSyncService)로 확인한다.
 */
@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
public class CardSyncController {

    private final CardSyncService cardSyncService;

    @PostMapping("/cards")
    public ApiResponse<Void> triggerFullSync() {
        if (cardSyncService.isRunning()) {
            throw new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING);
        }
        cardSyncService.syncAllAsync();
        return ApiResponse.ok("Scrydex 카드 전체 동기화를 시작했습니다. 진행 상황은 서버 로그를 확인하세요.");
    }
}
