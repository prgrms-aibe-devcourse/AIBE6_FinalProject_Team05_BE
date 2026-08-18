package com.pokade.domain.admin.service;

import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryImage;
import com.pokade.domain.inquiry.repository.InquiryImageRepository;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryImageRepository inquiryImageRepository;
    private final S3FileStorage s3FileStorage;

    public List<InquiryResponse> getInquiries() {
        List<Inquiry> inquiries = inquiryRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, List<String>> imageUrlsByInquiryId = loadImageUrls(inquiries);
        return inquiries.stream()
                .map(inquiry -> InquiryResponse.of(inquiry, imageUrlsByInquiryId.getOrDefault(inquiry.getId(), List.of())))
                .toList();
    }

    public InquiryResponse getInquiry(Long id) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        List<String> imageUrls = inquiryImageRepository.findByInquiryIdOrderByIdAsc(id).stream()
                .map(image -> s3FileStorage.generatePresignedUrl(image.getImageUrl()))
                .toList();
        return InquiryResponse.of(inquiry, imageUrls);
    }

    private Map<Long, List<String>> loadImageUrls(List<Inquiry> inquiries) {
        if (inquiries.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = inquiries.stream().map(Inquiry::getId).toList();
        return inquiryImageRepository.findByInquiryIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(
                        InquiryImage::getInquiryId,
                        Collectors.mapping(image -> s3FileStorage.generatePresignedUrl(image.getImageUrl()), Collectors.toList())));
    }
}
