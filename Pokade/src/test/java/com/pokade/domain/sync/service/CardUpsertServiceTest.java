package com.pokade.domain.sync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.ExpansionRepository;
import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;
import com.pokade.domain.sync.client.dto.TranslationDto;

/**
 * sv10_ja처럼 translation 없이 먼저 동기화됐던 기존 세트를 재동기화할 때,
 * CardUpsertService.syncCard()가 세트명(Card.setName)을 translation(영문) 우선으로 채우는지,
 * 그리고 translation이 없거나 깨져 있어도 예외 없이 원본 이름으로 폴백하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CardUpsertServiceTest {

    @Mock
    private ExpansionRepository expansionRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardVariantRepository cardVariantRepository;

    @Mock
    private CardPriceRepository cardPriceRepository;

    @InjectMocks
    private CardUpsertService cardUpsertService;

    private static final String EXPANSION_ID = "sv10_ja";
    private static final String ORIGINAL_EXPANSION_NAME = "サンダー";

    private Expansion existingExpansion(String translation) {
        return Expansion.builder()
                .id(EXPANSION_ID)
                .name(ORIGINAL_EXPANSION_NAME)
                .translation(translation)
                .syncedAt(LocalDateTime.now().minusDays(13))
                .build();
    }

    private CardDto cardDto(TranslationDto translation) {
        ExpansionDto expansionDto = new ExpansionDto(
                EXPANSION_ID, ORIGINAL_EXPANSION_NAME, "Scarlet & Violet", null, 98, null,
                "JA", "2024/11/01", null, null, null, translation
        );
        return new CardDto(
                "sv10_ja-1", "クヌギダマ", "ポケモン", List.of("草"), List.of("たね"),
                "通常", "●", null, "YASHIRO Nanaco", List.of(204),
                "001/098", null, null, expansionDto, 1, "JA", null
        );
    }

    private Card capturedSavedCard() {
        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        org.mockito.Mockito.verify(cardRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("t1 translation이 비어있던 기존 세트도, 응답에 translation이 있으면 영문 세트명으로 백필/반영된다")
    void t1() {
        Expansion expansion = existingExpansion(null);
        given(expansionRepository.findById(EXPANSION_ID)).willReturn(Optional.of(expansion));
        given(cardRepository.findByExternalId("sv10_ja-1")).willReturn(Optional.empty());
        given(cardRepository.save(any(Card.class))).willAnswer(invocation -> invocation.getArgument(0));

        CardDto dto = cardDto(new TranslationDto(new TranslationDto.TranslationNameDto("Thunder")));

        boolean saved = cardUpsertService.upsertCard(dto);

        assertThat(saved).isTrue();
        assertThat(capturedSavedCard().getSetName()).isEqualTo("Thunder");
        assertThat(expansion.getTranslation()).isNotNull();
    }

    @Test
    @DisplayName("t2 응답에 translation이 없으면 기존처럼 원본 세트명 그대로 저장된다(회귀)")
    void t2() {
        Expansion expansion = existingExpansion(null);
        given(expansionRepository.findById(EXPANSION_ID)).willReturn(Optional.of(expansion));
        given(cardRepository.findByExternalId("sv10_ja-1")).willReturn(Optional.empty());
        given(cardRepository.save(any(Card.class))).willAnswer(invocation -> invocation.getArgument(0));

        CardDto dto = cardDto(null);

        boolean saved = cardUpsertService.upsertCard(dto);

        assertThat(saved).isTrue();
        assertThat(capturedSavedCard().getSetName()).isEqualTo(ORIGINAL_EXPANSION_NAME);
        assertThat(expansion.getTranslation()).isNull();
    }

    @Test
    @DisplayName("t3 translation JSON이 깨진 형식이면 파싱 예외 없이 원본 세트명으로 폴백한다")
    void t3() {
        Expansion expansion = existingExpansion("Thunder");
        given(expansionRepository.findById(EXPANSION_ID)).willReturn(Optional.of(expansion));
        given(cardRepository.findByExternalId("sv10_ja-1")).willReturn(Optional.empty());
        given(cardRepository.save(any(Card.class))).willAnswer(invocation -> invocation.getArgument(0));

        CardDto dto = cardDto(new TranslationDto(new TranslationDto.TranslationNameDto("Ignored")));

        boolean saved = cardUpsertService.upsertCard(dto);

        assertThat(saved).isTrue();
        assertThat(capturedSavedCard().getSetName()).isEqualTo(ORIGINAL_EXPANSION_NAME);
        assertThat(expansion.getTranslation()).isEqualTo("Thunder");
    }

    @Test
    @DisplayName("t4 translation.en.name이 빈 문자열이면 null로 취급되어 원본 세트명으로 폴백하고, 백필도 스킵되어 다음 기회에 백필 가능한 상태로 남는다")
    void t4() {
        Expansion expansion = existingExpansion(null);
        given(expansionRepository.findById(EXPANSION_ID)).willReturn(Optional.of(expansion));
        given(cardRepository.findByExternalId("sv10_ja-1")).willReturn(Optional.empty());
        given(cardRepository.save(any(Card.class))).willAnswer(invocation -> invocation.getArgument(0));

        CardDto dto = cardDto(new TranslationDto(new TranslationDto.TranslationNameDto("")));

        boolean saved = cardUpsertService.upsertCard(dto);

        assertThat(saved).isTrue();
        assertThat(capturedSavedCard().getSetName()).isEqualTo(ORIGINAL_EXPANSION_NAME);
        assertThat(expansion.getTranslation()).isNull();
    }

    @Test
    @DisplayName("t5 translation.en.name이 공백뿐이면 null로 취급되어 원본 세트명으로 폴백하고, 백필도 스킵되어 다음 기회에 백필 가능한 상태로 남는다")
    void t5() {
        Expansion expansion = existingExpansion(null);
        given(expansionRepository.findById(EXPANSION_ID)).willReturn(Optional.of(expansion));
        given(cardRepository.findByExternalId("sv10_ja-1")).willReturn(Optional.empty());
        given(cardRepository.save(any(Card.class))).willAnswer(invocation -> invocation.getArgument(0));

        CardDto dto = cardDto(new TranslationDto(new TranslationDto.TranslationNameDto("   ")));

        boolean saved = cardUpsertService.upsertCard(dto);

        assertThat(saved).isTrue();
        assertThat(capturedSavedCard().getSetName()).isEqualTo(ORIGINAL_EXPANSION_NAME);
        assertThat(expansion.getTranslation()).isNull();
    }
}
