package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.repository.CardRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;

    @Transactional(readOnly = true)
    public Page<CardResponse> search(String name, String types, String rarity, String expansionId, Pageable pageable) {
        return cardRepository.search(name, types, rarity, expansionId, pageable)
                .map(CardResponse::from);
    }
}