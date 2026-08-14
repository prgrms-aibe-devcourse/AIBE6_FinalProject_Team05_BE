package com.pokade.domain.sync.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardPrice;
import com.pokade.domain.card.entity.CardSyncFields;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.repository.ExpansionRepository;
import com.pokade.domain.sync.client.dto.CardDto;
import com.pokade.domain.sync.client.dto.CardPriceDto;
import com.pokade.domain.sync.client.dto.CardVariantDto;
import com.pokade.domain.sync.client.dto.ExpansionDto;
import com.pokade.domain.sync.client.dto.ImageDto;
import com.pokade.domain.sync.client.dto.TranslationDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카드 1건을 expansions → cards → card_variants → card_prices 순서로 upsert한다.
 * {@link CardSyncService}가 아니라 별도 빈으로 분리한 이유: @Transactional은 스프링 프록시를 통해서만
 * 적용되는데, CardSyncService가 페이지 안에서 이 메서드를 this.으로 직접 호출하면(self-invocation)
 * 트랜잭션이 걸리지 않는다 - 그래서 다른 빈으로 분리해 실제 프록시를 거치도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardUpsertService {

    private static final String NM_CONDITION = "NM";
    private static final String RAW_PRICE_TYPE = "raw";
    private static final String RAW_GRADE = "";
    private static final String RAW_COMPANY = "";
    private static final DateTimeFormatter RELEASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final ExpansionRepository expansionRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository cardVariantRepository;
    private final CardPriceRepository cardPriceRepository;
    // 앱의 Jackson 자동설정 ObjectMapper 빈에 의존하지 않고, translation 문자열 하나를 JSON으로
    // 감싸는 용도로만 쓰는 전용 인스턴스 - @RequiredArgsConstructor 생성자 파라미터에서 제외된다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return 실제로 카드/판본/가격을 저장했으면 true, is_online_only 방어 코드에 걸려 skip했으면 false
     */
    @Transactional
    public boolean upsertCard(CardDto dto) {
        ExpansionDto expansionDto = dto.expansion();
        if (expansionDto != null && Boolean.TRUE.equals(expansionDto.isOnlineOnly())) {
            log.warn("is_online_only=true 카드가 필터를 통과해 응답에 포함됨 - skip. externalId={}", dto.id());
            return false;
        }
        // 카드 이름은 카드의 정체성 자체라 blank/null이면 세트/판본/가격까지 갈 필요 없이 여기서 건너뛴다.
        // Card.name은 nullable=false라 그대로 저장을 시도하면 DataIntegrityViolationException으로
        // (CardSyncService의 카드별 try/catch에 잡히긴 하지만) 불필요하게 실패 처리되므로 미리 막는다.
        if (dto.name() == null || dto.name().isBlank()) {
            log.warn("카드 이름이 없어(blank/null) 동기화를 건너뜁니다. externalId={}", dto.id());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        Expansion expansion = expansionDto == null ? null : syncExpansion(expansionDto, now);
        Card card = syncCard(dto, expansion, now);

        CardVariantDto primaryVariant = selectPrimaryVariant(dto.variants());
        if (primaryVariant == null) {
            return true;
        }
        // 판본 이름도 blank/null이면(대표 판본 선정 자체는 가능했더라도) 판본/가격 동기화만 건너뛴다 -
        // 카드 자체는 이미 정상 저장됐으므로 그 값은 유지한다.
        if (primaryVariant.name() == null || primaryVariant.name().isBlank()) {
            log.warn("대표 판본 이름이 없어(blank/null) 판본/가격 동기화를 건너뜁니다. externalId={}", dto.id());
            return true;
        }

        CardVariant variant = syncVariant(card, primaryVariant, now);
        syncPrice(variant, primaryVariant, now);
        return true;
    }

    private Expansion syncExpansion(ExpansionDto dto, LocalDateTime now) {
        return expansionRepository.findById(dto.id())
                .map(existing -> backfillTranslation(existing, dto))
                .orElseGet(() -> createExpansion(dto, now));
    }

    /**
     * 세트 이름이 없으면(blank/null) 세트 생성을 건너뛰고 null을 반환한다 - 호출부(upsertCard)가
     * expansionDto == null일 때와 동일하게 처리하므로(Card.expansion은 nullable), 카드 자체 저장에는
     * 영향이 없다.
     */
    private Expansion createExpansion(ExpansionDto dto, LocalDateTime now) {
        if (dto.name() == null || dto.name().isBlank()) {
            log.warn("세트 이름이 없어(blank/null) 세트 동기화를 건너뜁니다. expansionId={}", dto.id());
            return null;
        }
        return expansionRepository.save(Expansion.builder()
                .id(dto.id())
                .name(dto.name())
                .series(dto.series())
                .code(dto.code())
                .total(dto.total())
                .languageCode(dto.languageCode())
                .releaseDate(parseReleaseDate(dto.releaseDate()))
                .logo(dto.logo())
                .symbol(dto.symbol())
                .translation(toJsonString(extractTranslationName(dto.translation())))
                .syncedAt(now)
                .build());
    }

    /**
     * translation 없이 먼저 동기화됐던 기존 세트를 위한 백필 - 이미 값이 있으면 덮어쓰지 않고,
     * dto에도 새 값이 없으면 그대로 둔다. 세트명 외 다른 필드(name/series 등)는 이 메서드에서 건드리지 않는다.
     */
    private Expansion backfillTranslation(Expansion existing, ExpansionDto dto) {
        if (existing.getTranslation() != null) {
            return existing;
        }
        String translation = toJsonString(extractTranslationName(dto.translation()));
        if (translation == null) {
            return existing;
        }
        existing.applyTranslationBackfill(translation);
        return existing;
    }

    private Card syncCard(CardDto dto, Expansion expansion, LocalDateTime now) {
        ImageDto image = firstImage(dto.images());
        CardSyncFields fields = new CardSyncFields(
                dto.name(),
                resolveExpansionName(expansion),
                dto.rarity(),
                dto.supertype(),
                dto.subtypes(),
                dto.types(),
                dto.evolvesFrom(),
                dto.printedNumber(),
                dto.rarityCode(),
                dto.hp(),
                dto.artist(),
                dto.nationalPokedexNumbers(),
                image != null ? image.small() : null,
                image != null ? image.medium() : null,
                image != null ? image.large() : null,
                expansion,
                dto.expansionSortOrder(),
                dto.languageCode(),
                now
        );

        return cardRepository.findByExternalId(dto.id())
                .map(existing -> {
                    existing.applySync(fields);
                    return existing;
                })
                .orElseGet(() -> cardRepository.save(fields.toNewCard(dto.id())));
    }

    /**
     * 판본 1개면 그대로, 여러 개면 raw NM market이 가장 저렴한 판본을 대표로 선정한다.
     * NM 가격으로 비교 가능한 판본이 하나도 없으면 첫 번째 판본을 fallback으로 채택한다.
     */
    private CardVariantDto selectPrimaryVariant(List<CardVariantDto> variants) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        if (variants.size() == 1) {
            return variants.get(0);
        }

        CardVariantDto cheapest = null;
        java.math.BigDecimal cheapestMarket = null;
        for (CardVariantDto variant : variants) {
            CardPriceDto nm = findNmPrice(variant);
            if (nm == null || nm.market() == null) {
                continue;
            }
            if (cheapestMarket == null || nm.market().compareTo(cheapestMarket) < 0) {
                cheapestMarket = nm.market();
                cheapest = variant;
            }
        }
        return cheapest != null ? cheapest : variants.get(0);
    }

    private CardVariant syncVariant(Card card, CardVariantDto dto, LocalDateTime now) {
        ImageDto image = firstImage(dto.images());
        String imageSmall = image != null ? image.small() : null;
        String imageLarge = image != null ? image.large() : null;

        return cardVariantRepository.findByCardId(card.getId())
                .map(existing -> {
                    existing.applySync(dto.name(), imageSmall, imageLarge, now);
                    return existing;
                })
                .orElseGet(() -> cardVariantRepository.save(CardVariant.builder()
                        .card(card)
                        .variantName(dto.name())
                        .primary(true)
                        .imageSmall(imageSmall)
                        .imageLarge(imageLarge)
                        .syncedAt(now)
                        .build()));
    }

    private void syncPrice(CardVariant variant, CardVariantDto variantDto, LocalDateTime now) {
        CardPriceDto nm = findNmPrice(variantDto);
        if (nm == null) {
            return;
        }

        cardPriceRepository.findByVariantIdAndPriceTypeAndGradeAndCompany(
                        variant.getId(), RAW_PRICE_TYPE, RAW_GRADE, RAW_COMPANY)
                .ifPresentOrElse(
                        existing -> existing.applySync(nm.low(), nm.mid(), nm.high(), nm.market(), nm.currency(), now),
                        () -> cardPriceRepository.save(CardPrice.builder()
                                .variant(variant)
                                .priceType(RAW_PRICE_TYPE)
                                .low(nm.low())
                                .mid(nm.mid())
                                .high(nm.high())
                                .market(nm.market())
                                .currency(nm.currency())
                                .updatedAt(now)
                                .build())
                );
    }

    private CardPriceDto findNmPrice(CardVariantDto variant) {
        if (variant.prices() == null) {
            return null;
        }
        return variant.prices().stream()
                .filter(p -> NM_CONDITION.equalsIgnoreCase(p.condition()))
                .findFirst()
                .orElse(null);
    }

    private ImageDto firstImage(List<ImageDto> images) {
        return images == null || images.isEmpty() ? null : images.get(0);
    }

    private LocalDate parseReleaseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw, RELEASE_DATE_FORMAT);
    }

    /**
     * 세트명은 translation(en.name) 영문 표기가 있으면 그것을 우선 사용하고,
     * 없거나 파싱에 실패하면 기존처럼 expansion.getName()(원본 언어 표기)으로 폴백한다.
     */
    private String resolveExpansionName(Expansion expansion) {
        if (expansion == null) {
            return null;
        }
        String translation = expansion.getTranslation();
        if (translation == null) {
            return expansion.getName();
        }
        try {
            return objectMapper.readValue(translation, String.class);
        } catch (JsonProcessingException e) {
            log.warn("expansion.translation 파싱 실패 - name으로 폴백. expansionId={}, translation={}",
                    expansion.getId(), translation, e);
            return expansion.getName();
        }
    }

    private String extractTranslationName(TranslationDto translation) {
        if (translation == null || translation.en() == null) {
            return null;
        }
        String name = translation.en().name();
        if (name == null || name.isBlank()) {
            return null;
        }
        return name;
    }

    /** translation JSONB 컬럼에는 en.name 문자열 하나만 JSON 문자열 값으로 저장한다({"en":{"name":...}} 구조 그대로 넣지 않음). */
    private String toJsonString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("translation 값을 JSON으로 변환하지 못했습니다: " + value, e);
        }
    }
}
