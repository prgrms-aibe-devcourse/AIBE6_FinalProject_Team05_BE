package com.pokade.domain.inquiry.dto.response;

import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryCategory;

import java.time.LocalDateTime;
import java.util.List;

public record InquiryResponse(
        Long id,
        Long userId,
        InquiryCategory category,
        String title,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt
) {

    public static InquiryResponse of(Inquiry inquiry, List<String> imageUrls) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getUserId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getContent(),
                imageUrls,
                inquiry.getCreatedAt()
        );
    }
}
