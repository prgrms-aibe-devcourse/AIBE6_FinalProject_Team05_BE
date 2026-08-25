package com.pokade.domain.watchlist.controller;

import com.pokade.domain.watchlist.dto.WatchlistCountResponse;
import com.pokade.domain.watchlist.dto.WatchlistCreateRequest;
import com.pokade.domain.watchlist.dto.WatchlistResponse;
import com.pokade.domain.watchlist.dto.WatchlistUpdateRequest;
import com.pokade.domain.watchlist.service.WatchlistService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "워치리스트", description = "관심 카드 등록·목표가 설정·삭제 API")
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @Operation(
            summary = "워치리스트 등록",
            description = "카드를 관심 목록에 등록합니다. 목표가는 선택 입력이며, 등록 시점에 이미 목표가에 "
                    + "도달해 있으면 배치를 기다리지 않고 곧바로 알림을 만듭니다. 이미 등록한 카드이거나 "
                    + "보유 한도(20개)를 넘으면 실패합니다."
    )
    @PostMapping
    public ApiResponse<WatchlistResponse> addWatchlist(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WatchlistCreateRequest request
    ) {
        return ApiResponse.ok("워치리스트에 등록되었습니다.", watchlistService.addWatchlist(userId, request));
    }

    @Operation(summary = "내 워치리스트 조회", description = "로그인한 회원의 관심 카드 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<WatchlistResponse>> getWatchlist(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(watchlistService.getWatchlist(userId));
    }

    @Operation(
            summary = "카드별 관심 등록 수 조회",
            description = "여러 카드의 관심 등록 수를 한 번에 조회합니다. 비로그인 상태에서도 호출할 수 있습니다."
    )
    @GetMapping("/counts")
    public ApiResponse<List<WatchlistCountResponse>> getWatchlistCounts(
            @Parameter(description = "카드 ID 목록") @RequestParam List<Long> cardIds) {
        return ApiResponse.ok(watchlistService.getWatchlistCounts(cardIds));
    }

    @Operation(
            summary = "목표가 수정",
            description = "등록된 관심 카드의 목표가를 수정합니다. 본인이 등록한 항목만 수정할 수 있습니다."
    )
    @PatchMapping("/{id}")
    public ApiResponse<WatchlistResponse> updateWatchlist(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "워치리스트 ID") @PathVariable Long id,
            @Valid @RequestBody WatchlistUpdateRequest request
    ) {
        return ApiResponse.ok("목표가가 수정되었습니다.", watchlistService.updateWatchlist(userId, id, request));
    }

    @Operation(
            summary = "워치리스트 삭제",
            description = "관심 목록에서 카드를 제거합니다. 본인이 등록한 항목만 삭제할 수 있습니다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteWatchlist(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "워치리스트 ID") @PathVariable Long id
    ) {
        watchlistService.deleteWatchlist(userId, id);
        return ApiResponse.ok("워치리스트에서 삭제되었습니다.");
    }
}
