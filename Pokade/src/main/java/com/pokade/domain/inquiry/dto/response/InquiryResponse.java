package com.pokade.domain.inquiry.dto.response;

import com.pokade.domain.inquiry.entity.Inquiry;

import java.time.LocalDateTime;

public record InquiryResponse(
        Long id,
        Long userId,
        String title,
        String content,
        LocalDateTime createdAt
) {

    public static InquiryResponse of(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getUserId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getCreatedAt()
        );
    }
}
