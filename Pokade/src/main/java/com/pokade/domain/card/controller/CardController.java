package com.pokade.domain.card.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.service.CardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @GetMapping
    public Page<CardResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String rarity,
            @RequestParam(required = false) String expansionId,
            @PageableDefault(size = 20) Pageable pageable) {
        return cardService.search(name, types, rarity, expansionId, pageable);
    }

    @GetMapping("/{id}")
    public CardDetailResponse detail(@PathVariable Long id) {
        return cardService.getDetail(id);
    }
}