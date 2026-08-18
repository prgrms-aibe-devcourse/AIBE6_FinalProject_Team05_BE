package com.pokade.domain.watchlist.controller;

import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.dto.WatchlistUpdateRequest;
import com.pokade.domain.watchlist.service.WatchlistService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @PostMapping
    public ApiResponse<WatchlistResponse> addWatchlist(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WatchlistCreateRequest request
    ) {
        return ApiResponse.ok("워치리스트에 등록되었습니다.", watchlistService.addWatchlist(userId, request));
    }

    @GetMapping
    public ApiResponse<List<WatchlistResponse>> getWatchlist(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(watchlistService.getWatchlist(userId));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WatchlistResponse> updateWatchlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WatchlistUpdateRequest request
    ) {
        return ApiResponse.ok("목표가가 수정되었습니다.", watchlistService.updateWatchlist(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteWatchlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        watchlistService.deleteWatchlist(userId, id);
        return ApiResponse.ok("워치리스트에서 삭제되었습니다.");
    }
}
