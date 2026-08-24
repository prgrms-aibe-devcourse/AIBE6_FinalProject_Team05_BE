package com.pokade.domain.inquiry.service;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryImage;
import com.pokade.domain.inquiry.repository.InquiryImageRepository;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
    // #392: 문의 등록 시 관리자 전원에게 알림을 보내기 위한 의존성 - createInquiry()에서만 쓴다.
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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

        // #392: 활성 관리자 전원에게 새 문의 도착을 알린다. 관리자가 없으면 NotificationService가 조용히
        // 넘어가므로(빈 목록 가드) 관리자 부재로 문의 등록이 실패하지 않는다. 아래 S3 업로드가 실패하면
        // 이 알림도 같은 트랜잭션에서 롤백되고, AFTER_COMMIT이라 SSE 푸시도 나가지 않는다 - 의도한 동작이다.
        notificationService.createInquiryReceivedNotification(
                userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE).stream().map(User::getId).toList(),
                inquiry.getId(),
                inquiry.getTitle());

        List<String> imageUrls = uploadImages(inquiry.getId(), files);

        return InquiryResponse.of(inquiry, imageUrls);
    }

    // 업로드된 S3 key를 추적하다가, 중간에 실패하면 이미 올라간 객체를 지우고 예외를 다시 던진다 -
    // @Transactional은 DB만 롤백할 뿐 이미 성공한 S3 PutObject는 되돌리지 않으므로 고아 객체가 남는다.
    private List<String> uploadImages(Long inquiryId, List<MultipartFile> files) {
        List<String> uploadedKeys = new ArrayList<>();
        try {
            return files.stream()
                    .map(file -> {
                        String key = s3FileStorage.upload(file, FOLDER);
                        uploadedKeys.add(key);
                        inquiryImageRepository.save(InquiryImage.builder().inquiryId(inquiryId).imageUrl(key).build());
                        return key;
                    })
                    .map(s3FileStorage::generatePresignedUrl)
                    .toList();
        } catch (RuntimeException e) {
            for (String key : uploadedKeys) {
                try {
                    s3FileStorage.delete(key);
                } catch (RuntimeException deleteEx) {
                    log.error("문의 첨부 이미지 업로드 실패 후 S3 객체 정리 실패 - inquiryId={} (고아 객체 잔존)", inquiryId, deleteEx);
                }
            }
            throw e;
        }
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
            // Content-Type은 클라이언트가 보낸 헤더라 위조 가능 - 실제 파일 시그니처(매직 바이트)로 검증한다.
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType()) || !isJpegOrPng(file)) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
            }
        }
    }

    private boolean isJpegOrPng(MultipartFile file) {
        byte[] header = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < 3) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        boolean isPng = (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
        boolean isJpeg = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
        return isPng || isJpeg;
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
