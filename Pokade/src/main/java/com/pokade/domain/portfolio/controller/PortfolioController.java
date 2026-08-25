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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "도감", description = "보유 카드 도감(포트폴리오) 등록·수익률·세트 완성도 조회 API")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(
            summary = "도감에 카드 추가",
            description = "보유 중인 카드를 매입가·수량과 함께 도감에 등록합니다. 존재하지 않는 카드나 변형이면 "
                    + "실패하고, 정상(ACTIVE) 상태가 아닌 계정은 등록할 수 없습니다."
    )
    @PostMapping
    public ApiResponse<PortfolioItemResponse> addItem(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PortfolioItemAddRequest request
    ) {
        return ApiResponse.ok("포트폴리오에 추가되었습니다.", portfolioService.addItem(userId, request));
    }

    @Operation(summary = "내 도감 조회", description = "로그인한 회원이 등록한 도감 항목 전체를 조회합니다.")
    @GetMapping
    public ApiResponse<List<PortfolioItemResponse>> getMyPortfolio(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getMyPortfolio(userId));
    }

    @Operation(
            summary = "도감 요약 조회",
            description = "총 평가금액·총 매입금액·전체 손익 등 도감 전체 요약을 조회합니다."
    )
    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryResponse> getSummary(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getSummary(userId));
    }

    @Operation(
            summary = "항목별 손익 조회",
            description = "도감 항목 한 건의 현재 시세 기준 평가손익을 조회합니다. 본인 항목만 조회할 수 있습니다."
    )
    @GetMapping("/{id}/pnl")
    public ApiResponse<PortfolioItemPnlResponse> getPnl(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "도감 항목 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(portfolioService.getPnl(userId, id));
    }

    @Operation(
            summary = "도감 분석 조회",
            description = "보유 구성 비중·수익률 분포 등 도감 분석 지표를 조회합니다."
    )
    @GetMapping("/analytics")
    public ApiResponse<PortfolioAnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getAnalytics(userId));
    }

    // FR-PORT-07: 세트 완성도 - 보유한 세트별로 전체 카드 중 몇 %를 모았는지.
    @Operation(
            summary = "세트 완성도 조회",
            description = "보유한 세트별로 전체 카드 중 몇 퍼센트를 모았는지 조회합니다."
    )
    @GetMapping("/set-completion")
    public ApiResponse<List<PortfolioSetCompletionResponse>> getSetCompletion(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.ok(portfolioService.getSetCompletion(userId));
    }

    // FR-AI-04: AI 등급 진단 결과를 바탕으로 도감에 카드 즉시 등록.
    // request 바디는 선택 — AI가 카드를 인식 못했거나 잘못 인식했을 때 사용자가 고른 카드로 덮어쓴다.
    @Operation(
            summary = "AI 진단 결과로 도감 등록",
            description = "AI 등급 진단 결과를 사용해 도감에 카드를 등록하며, 진단에 사용한 사진이 표지가 됩니다. "
                    + "요청 바디는 선택이며, AI가 카드를 인식하지 못했거나 잘못 인식한 경우 사용자가 고른 "
                    + "카드로 덮어쓸 때만 보냅니다. 본인의 진단 결과만 사용할 수 있고, 진단이 성공 상태가 "
                    + "아니거나 이미 도감에 등록한 결과면 실패합니다."
    )
    @PostMapping("/from-grade/{resultId}")
    public ApiResponse<PortfolioItemResponse> addFromGradeResult(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "AI 등급 진단 결과 ID") @PathVariable Long resultId,
            @RequestBody(required = false) PortfolioFromGradeRequest request
    ) {
        Long overrideCardId = request != null ? request.cardId() : null;
        Long overrideVariantId = request != null ? request.variantId() : null;
        return ApiResponse.ok("도감에 등록되었습니다.",
                portfolioService.addFromGradeResult(userId, resultId, overrideCardId, overrideVariantId));
    }

    // 도감 항목 표지 사진 교체 — AI 진단 등록 여부와 무관하게 항상 가능.
    @Operation(
            summary = "도감 항목 표지 사진 변경",
            description = "도감 항목의 표지 사진을 업로드해 교체합니다(multipart/form-data). AI 진단으로 등록한 "
                    + "항목인지와 무관하게 사용할 수 있으며, 본인 항목만 변경할 수 있습니다."
    )
    @PostMapping("/{id}/thumbnail")
    public ApiResponse<PortfolioItemResponse> setThumbnail(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "도감 항목 ID") @PathVariable Long id,
            @RequestPart MultipartFile image
    ) {
        return ApiResponse.ok("표지 사진이 변경되었습니다.", portfolioService.setThumbnail(userId, id, image));
    }

    @Operation(
            summary = "도감 항목 수정",
            description = "도감 항목의 매입가·수량 등을 수정합니다. 본인 항목만 수정할 수 있습니다."
    )
    @PutMapping("/{id}")
    public ApiResponse<PortfolioItemResponse> updateItem(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "도감 항목 ID") @PathVariable Long id,
            @Valid @RequestBody PortfolioItemUpdateRequest request
    ) {
        return ApiResponse.ok("포트폴리오 항목이 수정되었습니다.", portfolioService.updateItem(userId, id, request));
    }

    @Operation(
            summary = "도감 항목 삭제",
            description = "도감에서 항목을 제거합니다. 본인 항목만 삭제할 수 있습니다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteItem(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "도감 항목 ID") @PathVariable Long id
    ) {
        portfolioService.deleteItem(userId, id);
        return ApiResponse.ok("포트폴리오에서 삭제되었습니다.");
    }
}
