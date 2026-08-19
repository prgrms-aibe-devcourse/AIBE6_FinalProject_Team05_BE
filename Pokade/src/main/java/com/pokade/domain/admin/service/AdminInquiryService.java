package com.pokade.domain.admin.service;

import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryCategory;
import com.pokade.domain.inquiry.entity.InquiryImage;
import com.pokade.domain.inquiry.entity.InquiryStatus;
import com.pokade.domain.inquiry.repository.InquiryImageRepository;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final NotificationService notificationService;

    public Page<InquiryResponse> getInquiries(InquiryCategory category, Pageable pageable) {
        Page<Inquiry> inquiries = category != null
                ? inquiryRepository.findByCategoryOrderByCreatedAtDesc(category, pageable)
                : inquiryRepository.findAllByOrderByCreatedAtDesc(pageable);
        Map<Long, List<String>> imageUrlsByInquiryId = loadImageUrls(inquiries.getContent());
        return inquiries.map(inquiry ->
                InquiryResponse.of(inquiry, imageUrlsByInquiryId.getOrDefault(inquiry.getId(), List.of())));
    }

    public InquiryResponse getInquiry(Long id) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        List<String> imageUrls = inquiryImageRepository.findByInquiryIdOrderByIdAsc(id).stream()
                .map(image -> s3FileStorage.generatePresignedUrl(image.getImageUrl()))
                .toList();
        return InquiryResponse.of(inquiry, imageUrls);
    }

    @Transactional
    public InquiryResponse updateStatus(Long id, InquiryStatus status) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        InquiryStatus previousStatus = inquiry.getStatus();
        inquiry.changeStatus(status);
        if (previousStatus != InquiryStatus.HANDLED && status == InquiryStatus.HANDLED) {
            notificationService.createInquiryHandledNotification(inquiry.getUserId(), inquiry.getTitle());
        }
        List<String> imageUrls = inquiryImageRepository.findByInquiryIdOrderByIdAsc(id).stream()
                .map(image -> s3FileStorage.generatePresignedUrl(image.getImageUrl()))
                .toList();
        return InquiryResponse.of(inquiry, imageUrls);
    }

    @Transactional
    public InquiryResponse answerInquiry(Long id, String content) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
        inquiry.answer(content);
        notificationService.createInquiryHandledNotification(inquiry.getUserId(), inquiry.getTitle());
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
