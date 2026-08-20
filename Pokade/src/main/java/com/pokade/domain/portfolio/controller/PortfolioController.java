package com.pokade.domain.portfolio.controller;

import com.pokade.domain.portfolio.dto.PortfolioAnalyticsResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemAddRequest;
import com.pokade.domain.portfolio.dto.PortfolioItemPnlResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemUpdateRequest;
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
import org.springframework.web.bind.annotation.RestController;

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
