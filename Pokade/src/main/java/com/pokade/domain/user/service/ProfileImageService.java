package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.event.ProfileImageCleanupEvent;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final String FOLDER = "profile";

    private final UserRepository userRepository;
    private final S3FileStorage s3FileStorage;
    private final ApplicationEventPublisher eventPublisher;

    // 프로필 이미지를 업로드하고 기존 이미지를 정리한다.
    @Transactional
    public void upload(Long userId, MultipartFile file) {
        validate(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newKey = s3FileStorage.upload(file, FOLDER);
        String previousKey = user.changeProfile(newKey);

        // 커밋이 확정된 뒤에 옛 객체를 지운다 - 롤백되면 DB는 예ㅛ key를 유지하므로 객체가 살아있어야 한다.
        publishCleanup(userId, previousKey);
    }

    // 프로필 이미지를 제거하고 기본 이미지로 되돌린다.
    @Transactional
    public void delete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getProfileImageUrl() == null) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_SET);
        }

        String previousKey = user.removeProfile();
        publishCleanup(userId, previousKey);
    }

    public ResponseEntity<Resource> serve(Long userId, String ifNoneMatch) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String key = user.getProfileImageUrl();
        if (key == null) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_SET);
        }

        HeadObjectResponse metadata;
        try {
            metadata = s3FileStorage.head(key);
        } catch (S3Exception e) {
            // 없는 객체(404)만 "이미지 없음"으로 바꾸고, 권한 오류 등 나머지는 그대로 올린다
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_SET);
            }
            throw e;
        }

        String eTag = metadata.eTag();
        if (eTag != null && eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).build();
        }

        Resource body = new InputStreamResource(s3FileStorage.openStream(key));
        return ResponseEntity.ok()
                .eTag(eTag)
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.contentLength())
                .cacheControl(CacheControl.noCache())
                .body(body);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private void publishCleanup(Long userId, String key) {
        if (key != null) {
            eventPublisher.publishEvent(new ProfileImageCleanupEvent(userId, key));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cleanupPreviousImage(ProfileImageCleanupEvent event) {
        try {
            s3FileStorage.delete(event.key());
        } catch (RuntimeException e) {
            log.error("프로필 이미지 S3 객체 삭제 실패 - userId={} (고아 객체 잔존)", event.userId(), e);
        }
    }

    private String extensionOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        int dot = (name == null) ? -1 : name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot).toLowerCase();
    }
}
