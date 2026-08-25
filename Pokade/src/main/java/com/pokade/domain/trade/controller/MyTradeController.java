package com.pokade.domain.trade.controller;

import com.pokade.domain.trade.dto.MyTradeResponse;
import com.pokade.domain.trade.dto.MyTradeSearchCondition;
import com.pokade.domain.trade.dto.TradeRole;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "거래", description = "즉시구매 및 거래 상태(발송/검수/배송/확정/취소) 관리 API")
@RestController
@RequestMapping("/api/users/me/trades")
@RequiredArgsConstructor
public class MyTradeController {

    private final TradeService tradeService;

    @Operation(
            summary = "내 거래 내역 조회",
            description = "로그인한 회원이 구매자 또는 판매자로 참여한 거래를 최신순으로 페이징 조회합니다. "
                    + "역할·상태·기간으로 필터링할 수 있으며 모든 조건은 선택 항목입니다."
    )
    @GetMapping
    public ApiResponse<Page<MyTradeResponse>> getMyTrades(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "거래 역할 (구매자/판매자)") @RequestParam(required = false) TradeRole role,
            @Parameter(description = "거래 상태 목록") @RequestParam(required = false) List<TradeStatus> status,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(tradeService.getMyTrades(
                userId, new MyTradeSearchCondition(role, status, from, to), pageable));
    }
}

