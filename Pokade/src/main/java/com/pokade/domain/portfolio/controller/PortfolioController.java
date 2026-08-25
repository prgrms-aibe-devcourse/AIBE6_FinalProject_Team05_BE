package com.pokade.domain.portfolio.controller;

import com.pokade.domain.portfolio.dto.PortfolioAnalyticsResponse;
import com.pokade.domain.portfolio.dto.PortfolioFromGradeRequest;
import com.pokade.domain.portfolio.dto.PortfolioItemAddRequest;
import com.pokade.domain.portfolio.dto.PortfolioItemPnlResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemUpdateRequest;
import com.pokade.domain.portfolio.dto.PortfolioSetCompletionResponse;
import com.pokade.domain.portfolio.dto.PortfolioSummaryResponse;
import com.pokade.domain.portfolio.service.PortfolioService;
import com.pokade.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping
    public ApiResponse<PortfolioItemResponse> addItem(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PortfolioItemAddRequest request
    ) {
        return ApiResponse.ok("포트폴리오에 추가되었습니다.", portfolioService.addItem(userId, request));
    }

    @GetMapping
    public ApiResponse<List<PortfolioItemResponse>> getMyPortfolio(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getMyPortfolio(userId));
    }

    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getSummary(userId));
    }

    @GetMapping("/{id}/pnl")
    public ApiResponse<PortfolioItemPnlResponse> getPnl(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(portfolioService.getPnl(userId, id));
    }

    @GetMapping("/analytics")
    public ApiResponse<PortfolioAnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getAnalytics(userId));
    }

    // FR-PORT-07: 세트 완성도 - 보유한 세트별로 전체 카드 중 몇 %를 모았는지.
    @GetMapping("/set-completion")
    public ApiResponse<List<PortfolioSetCompletionResponse>> getSetCompletion(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getSetCompletion(userId));
    }

    // FR-AI-04: AI 등급 진단 결과를 바탕으로 도감에 카드 즉시 등록.
    // request 바디는 선택 — AI가 카드를 인식 못했거나 잘못 인식했을 때 사용자가 고른 카드로 덮어쓴다.
    @PostMapping("/from-grade/{resultId}")
    public ApiResponse<PortfolioItemResponse> addFromGradeResult(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long resultId,
            @RequestBody(required = false) PortfolioFromGradeRequest request
    ) {
        Long overrideCardId = request != null ? request.cardId() : null;
        Long overrideVariantId = request != null ? request.variantId() : null;
        return ApiResponse.ok("도감에 등록되었습니다.",
                portfolioService.addFromGradeResult(userId, resultId, overrideCardId, overrideVariantId));
    }

    // 도감 항목 표지 사진 교체 — AI 진단 등록 여부와 무관하게 항상 가능.
    @PostMapping("/{id}/thumbnail")
    public ApiResponse<PortfolioItemResponse> setThumbnail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestPart MultipartFile image
    ) {
        return ApiResponse.ok("표지 사진이 변경되었습니다.", portfolioService.setThumbnail(userId, id, image));
    }

    @PutMapping("/{id}")
    public ApiResponse<PortfolioItemResponse> updateItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody PortfolioItemUpdateRequest request
    ) {
        return ApiResponse.ok("포트폴리오 항목이 수정되었습니다.", portfolioService.updateItem(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        portfolioService.deleteItem(userId, id);
        return ApiResponse.ok("포트폴리오에서 삭제되었습니다.");
    }
}
