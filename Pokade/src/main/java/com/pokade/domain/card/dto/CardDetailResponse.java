package com.pokade.domain.card.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.entity.CardVariant;
import com.pokade.domain.card.entity.Expansion;

public record CardDetailResponse(
        Long id,
        String externalId,
        String name,
        String nameKo,
        String languageCode,
        String setName,
        String rarity,
        String supertype,
        List<String> types,
        String artist,
        String printedNumber,
        String imageSmall,
        String imageMedium,
        String imageLarge,
        Integer viewCount,
        ExpansionSummary expansion,
        List<VariantSummary> variants
) {

    /**
     * viewCount는 card.getViewCount()로 읽지 않고 별도 파라미터로 받는다 - 호출부(CardQueryService.getDetail())가
     * incrementViewCounts()의 조회수 원자적 증가(UPDATE) 이후에도 이 조회 한 건에 한해 "증가된 값"을 응답에
     * 보여주기 위해 계산한 값을 넘기기 때문이다. card 엔티티 자체의 필드를 직접 mutate하지 않는 이유는,
     * 그러면 이 영속 엔티티가 dirty로 잡혀 트랜잭션 커밋 시 별도 UPDATE가 한 번 더 나가면서, 그 사이 다른
     * 요청이 원자적으로 증가시킨 최신 값을 이 요청의 "오래된 값+1"로 덮어써버리는 lost update가 재발할 수 있다.
     */
    public static CardDetailResponse of(Card card, List<CardVariant> variants, Map<Long, List<String>> gradesByVariantId, String nameKo, List<String> types, String rarity, Integer viewCount) {
        return new CardDetailResponse(
                card.getId(),
                card.getExternalId(),
                card.getName(),
                nameKo,
                card.getLanguageCode(),
                card.getSetName(),
                rarity,
                card.getSupertype(),
                types,
                card.getArtist(),
                card.getPrintedNumber(),
                card.getImageSmall(),
                card.getImageMedium(),
                card.getImageLarge(),
                viewCount,
                ExpansionSummary.from(card.getExpansion()),
                variants.stream()
                        .map(variant -> VariantSummary.from(variant, gradesByVariantId.getOrDefault(variant.getId(), List.of())))
                        .toList()
        );
    }

    public record ExpansionSummary(
            String id,
            String name,
            String series,
            String code,
            Integer total,
            LocalDate releaseDate,
            String logo,
            String symbol
    ) {

        public static ExpansionSummary from(Expansion expansion) {
            if (expansion == null) {
                return null;
            }
            return new ExpansionSummary(
                    expansion.getId(),
                    expansion.getName(),
                    expansion.getSeries(),
                    expansion.getCode(),
                    expansion.getTotal(),
                    expansion.getReleaseDate(),
                    expansion.getLogo(),
                    expansion.getSymbol()
            );
        }
    }

    public record VariantSummary(
            Long id,
            String variantName,
            boolean primary,
            String imageSmall,
            String imageLarge,
            List<String> grades
    ) {

        public static VariantSummary from(CardVariant variant, List<String> grades) {
            return new VariantSummary(
                    variant.getId(),
                    variant.getVariantName(),
                    variant.isPrimary(),
                    variant.getImageSmall(),
                    variant.getImageLarge(),
                    grades
            );
        }
    }
}
