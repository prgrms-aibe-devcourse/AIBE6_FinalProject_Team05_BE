package com.pokade.domain.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

// 비로그인 상태에서 localStorage에 쌓아둔 프리셋 클릭 기록(포인터)을 로그인 후 서버로 이관할 때 쓰는 요청.
// 답변 내용은 여기 담지 않는다 - presetId만 보내고, 실제 답변은 서버가 import 시점에 다시 조회해 만든다.
public record ChatHistoryImportRequest(
        @NotBlank(message = "sessionId는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "sessionId는 UUID 형식이어야 합니다."
        )
        String sessionId,

        @NotEmpty(message = "entries는 최소 1개 이상이어야 합니다.")
        @Size(max = 20, message = "entries는 최대 20개까지 가능합니다.")
        @Valid
        List<Entry> entries
) {
    public record Entry(
            @NotBlank(message = "presetId는 필수입니다.")
            String presetId,

            @NotNull(message = "askedAt은 필수입니다.")
            Instant askedAt
    ) {
    }
}
