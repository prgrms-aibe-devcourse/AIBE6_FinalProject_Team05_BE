package com.pokade.domain.card.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokade.domain.card.dto.CardDetailResponse;
import com.pokade.domain.card.dto.CardResponse;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;

    @Transactional(readOnly = true)
    public Page<CardResponse> search(String types, String rarity, String expansionId, Pageable pageable) {
        return cardRepository.search(types, rarity, expansionId, pageable)
                .map(CardResponse::from);
    }

    @Transactional(readOnly = true)
    public CardDetailResponse getDetail(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        List<CardVariant> variants = cardVariantRepository.findByCardIdOrderByPrimaryDescVariantNameAsc(id);
        return CardDetailResponse.of(card, variants);
    }

    @Transactional(readOnly = true)
    public Page<CardResponse> searchByKeyword(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return cardRepository.findByNameContainingIgnoreCase(q.trim(), pageable)
                .map(CardResponse::from);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getRelated(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        List<Card> related;
        if (hasPokedexNumber(card)) {
            related = cardRepository.findRelatedByPokedexNumber(id);
        } else if (card.getExpansion() != null) {
            related = cardRepository.findRelatedByExpansion(card.getExpansion().getId(), id);
        } else {
            related = List.of();
        }
        return related.stream()
                .map(CardResponse::from)
                .toList();
    }

    private boolean hasPokedexNumber(Card card) {
        return card.getNationalPokedexNumbers() != null && !card.getNationalPokedexNumbers().isEmpty();
    }
}