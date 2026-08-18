package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    private static final String ETAG = "\"abc123\"";

    @Mock
    private UserRepository userRepository;
    @Mock
    private S3FileStorage s3FileStorage;

    @InjectMocks
    private ProfileImageService profileImageService;

    private User userWithImage(String key) {
        User user = User.builder()
                .id(1L).email("user@pokade.com").password("ENCODED_PW")
                .nickname("지우").role(Role.USER).provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE).pointBalance(0)
                .build();
        if (key != null) {
            user.changeProfile(key);
        }
        return user;
    }

    private MultipartFile png(long size) {
        return new MockMultipartFile("image", "a.png", "image/png", new byte[(int) size]);
    }

    // ===== 업로드 =====

    @Test
    @DisplayName("업로드: S3에 저장하고 사용자 프로필 이미지 key를 갱신한다")
    void upload_success() {
        User user = userWithImage(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(s3FileStorage.upload(any(), any())).willReturn("profile/new-key.png");

        profileImageService.upload(1L, png(1024));

        assertThat(user.getProfileImageUrl()).isEqualTo("profile/new-key.png");
        then(s3FileStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("업로드: 교체 시 기존 S3 객체를 삭제한다")
    void upload_replacesAndDeletesPrevious() {
        User user = userWithImage("profile/old-key.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(s3FileStorage.upload(any(), any())).willReturn("profile/new-key.png");

        profileImageService.upload(1L, png(1024));

        assertThat(user.getProfileImageUrl()).isEqualTo("profile/new-key.png");
        then(s3FileStorage).should().delete("profile/old-key.png");
    }

    @Test
    @DisplayName("업로드: 5MB를 넘으면 FILE_TOO_LARGE, S3에 올리지 않는다")
    void upload_tooLarge() {
        assertThatThrownBy(() -> profileImageService.upload(1L, png(5 * 1024 * 1024 + 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);

        then(s3FileStorage).should(never()).upload(any(), any());
    }

    @Test
    @DisplayName("업로드: 미지원 형식이면 UNSUPPORTED_IMAGE_TYPE, S3에 올리지 않는다")
    void upload_unsupportedType() {
        MultipartFile pdf = new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> profileImageService.upload(1L, pdf))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);

        then(s3FileStorage).should(never()).upload(any(), any());
    }

    @Test
    @DisplayName("업로드: S3 삭제가 실패해도 교체 자체는 성공한다")
    void upload_s3DeleteFailureIsSwallowed() {
        User user = userWithImage("profile/old-key.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(s3FileStorage.upload(any(), any())).willReturn("profile/new-key.png");
        willThrow(new RuntimeException("S3 장애")).given(s3FileStorage).delete("profile/old-key.png");

        profileImageService.upload(1L, png(1024));

        assertThat(user.getProfileImageUrl()).isEqualTo("profile/new-key.png");
    }

    // ===== 삭제 =====

    @Test
    @DisplayName("삭제: 이미지를 지우고 컬럼을 비운다")
    void delete_success() {
        User user = userWithImage("profile/old-key.png");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        profileImageService.delete(1L);

        assertThat(user.getProfileImageUrl()).isNull();
        then(s3FileStorage).should().delete("profile/old-key.png");
    }

    @Test
    @DisplayName("삭제: 이미 기본 이미지면 PROFILE_IMAGE_NOT_SET")
    void delete_notSet() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithImage(null)));

        assertThatThrownBy(() -> profileImageService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_NOT_SET);

        then(s3FileStorage).should(never()).delete(anyString());
    }

    // ===== 프록시 서빙 =====

    @Test
    @DisplayName("서빙: ETag가 같으면 304를 반환하고 S3 본문을 읽지 않는다")
    void serve_notModified() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithImage("profile/key.png")));
        given(s3FileStorage.head("profile/key.png")).willReturn(
                HeadObjectResponse.builder().eTag(ETAG).contentType("image/png").contentLength(10L).build());

        ResponseEntity<Resource> res = profileImageService.serve(1L, ETAG);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(res.getBody()).isNull();
        assertThat(res.getHeaders().getETag()).isEqualTo(ETAG);
        then(s3FileStorage).should(never()).openStream(anyString());
    }

    @Test
    @DisplayName("서빙: ETag가 다르면 200과 이미지 본문을 반환한다")
    void serve_modified() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithImage("profile/key.png")));
        given(s3FileStorage.head("profile/key.png")).willReturn(
                HeadObjectResponse.builder().eTag(ETAG).contentType("image/png").contentLength(3L).build());
        given(s3FileStorage.openStream("profile/key.png")).willReturn(stream(new byte[]{1, 2, 3}));

        ResponseEntity<Resource> res = profileImageService.serve(1L, "\"stale\"");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getHeaders().getETag()).isEqualTo(ETAG);
        assertThat(res.getHeaders().getContentType()).hasToString("image/png");
        assertThat(res.getHeaders().getContentLength()).isEqualTo(3L);
    }

    @Test
    @DisplayName("서빙: If-None-Match가 없으면 200과 본문을 반환한다")
    void serve_withoutIfNoneMatch() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithImage("profile/key.png")));
        given(s3FileStorage.head("profile/key.png")).willReturn(
                HeadObjectResponse.builder().eTag(ETAG).contentType("image/png").contentLength(3L).build());
        given(s3FileStorage.openStream("profile/key.png")).willReturn(stream(new byte[]{1, 2, 3}));

        ResponseEntity<Resource> res = profileImageService.serve(1L, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(s3FileStorage).should().openStream("profile/key.png");
    }

    @Test
    @DisplayName("서빙: 이미지가 없는 사용자면 PROFILE_IMAGE_NOT_SET (USER_NOT_FOUND 아님)")
    void serve_imageNotSet() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithImage(null)));

        assertThatThrownBy(() -> profileImageService.serve(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_NOT_SET);
    }

    @Test
    @DisplayName("서빙: 존재하지 않는 사용자면 USER_NOT_FOUND")
    void serve_userNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileImageService.serve(99L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("서빙: DB에 key가 있어도 S3 객체가 없으면 PROFILE_IMAGE_NOT_SET")
    void serve_objectMissingInS3() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithImage("profile/key.png")));
        given(s3FileStorage.head("profile/key.png"))
                .willThrow(NoSuchKeyException.builder().message("not found").build());

        assertThatThrownBy(() -> profileImageService.serve(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_NOT_SET);
    }

    private ResponseInputStream<GetObjectResponse> stream(byte[] bytes) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    }
}
