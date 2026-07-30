package com.pokade.domain.trade.controller;

import com.pokade.domain.trade.dto.TradeCreateRequest;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PostMapping
    public ResponseEntity<TradeResponse> createTrade(
            // TODO: 인증 파트 완성되면 SecurityContext에서 buyerId 추출하는 방식으로 교체
            @RequestHeader("X-USER-ID") Long buyerId,
            @Valid @RequestBody TradeCreateRequest request
    ) {
        TradeResponse response = tradeService.createTrade(buyerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TradeResponse> getTrade(
            // TODO: 인증 파트 완성되면 SecurityContext에서 userId 추출하는 방식으로 교체
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(tradeService.getTrade(userId, id));
    }
}
