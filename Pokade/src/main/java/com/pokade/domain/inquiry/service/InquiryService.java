package com.pokade.domain.inquiry.service;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private static final int MAX_IMAGE_COUNT = 3;
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final String FOLDER = "inquiries";

    private final InquiryRepository inquiryRepository;
    private final InquiryImageRepository inquiryImageRepository;
    private final S3FileStorage s3FileStorage;

    @Transactional
    public InquiryResponse createInquiry(Long userId, InquiryCreateRequest request, List<MultipartFile> images) {
        List<MultipartFile> files = images == null ? List.of() : images;
        validateImages(files);

        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .build();
        inquiryRepository.save(inquiry);

        List<String> imageUrls = files.stream()
                .map(file -> s3FileStorage.upload(file, FOLDER))
                .peek(key -> inquiryImageRepository.save(
                        InquiryImage.builder().inquiryId(inquiry.getId()).imageUrl(key).build()))
                .map(s3FileStorage::generatePresignedUrl)
                .toList();

        return InquiryResponse.of(inquiry, imageUrls);
    }

    public List<InquiryResponse> getMyInquiries(Long userId) {
        List<Inquiry> inquiries = inquiryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, List<String>> imageUrlsByInquiryId = loadImageUrls(inquiries);
        return inquiries.stream()
                .map(inquiry -> InquiryResponse.of(inquiry, imageUrlsByInquiryId.getOrDefault(inquiry.getId(), List.of())))
                .toList();
    }

    private void validateImages(List<MultipartFile> files) {
        if (files.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.INQUIRY_IMAGE_LIMIT_EXCEEDED);
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
            }
        }
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
