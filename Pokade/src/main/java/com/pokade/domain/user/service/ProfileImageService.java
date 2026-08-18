package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

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

    // 프로필 이미지를 업로드하고 기존 이미지를 정리한다.
    @Transactional
    public void upload(Long userId, MultipartFile file) {
        validate(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newKey = s3FileStorage.upload(file, FOLDER);
        String previousKey = user.changeProfile(newKey);
        deleteQuietly(previousKey);
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
        deleteQuietly(previousKey);
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
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_SET);
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

    // S3 정리 실패가 이미지 교체, 삭제를 막지 않도록 로그만 남긴다.
    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            s3FileStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("프로필 이미지 S3 객체 삭제 실패 - key={}", key, e);
        }
    }
}
