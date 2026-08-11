package com.pokade.domain.sync.client.dto;

/**
 * 영어 외 언어 세트에만 존재하는 expansion.translation. 영어 세트는 API 응답에 이 필드 자체가 없다.
 */
public record TranslationDto(
        TranslationNameDto en
) {

    public record TranslationNameDto(String name) {
    }
}
