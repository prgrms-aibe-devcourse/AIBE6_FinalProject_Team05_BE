package com.pokade.global.infra.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3FileStorage s3FileStorage;

    private void injectBucket() {
        ReflectionTestUtils.setField(s3FileStorage, "bucket", BUCKET);
    }

    private MultipartFile file(String originalName) {
        return new MockMultipartFile("image", originalName, "image/png", new byte[]{1, 2, 3});
    }

    private PutObjectRequest capturePut() {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        then(s3Client).should().putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("업로드: key에 원본 파일명이 들어가지 않는다 (파일명에 개인정보가 섞일 수 있음)")
    void upload_keyDoesNotContainOriginalFilename() {
        injectBucket();

        String key = s3FileStorage.upload(file("이력서_홍길동_010-1234-5678.png"), "profile");

        assertThat(key).doesNotContain("홍길동").doesNotContain("010-1234-5678").doesNotContain("이력서");
        assertThat(key).matches("profile/[0-9a-f-]{36}\\.png");
        assertThat(capturePut().key()).isEqualTo(key);
    }

    @ParameterizedTest(name = "\"{0}\" → 확장자 \"{1}\"")
    @DisplayName("업로드: 확장자만 취하고 비정상 확장자는 버린다")
    @CsvSource(nullValues = "NULL", value = {
            "photo.png,        .png",
            "photo.JPEG,       .jpeg",
            "a.b.c.png,        .png",
            "noext,            ''",
            "'trailing.',      ''",
            "'x.한글',          ''",
            "'x.pn g',         ''",
            "'x.verylongextension', ''",
            "NULL,             ''",
    })
    void upload_extensionHandling(String originalName, String expectedSuffix) {
        injectBucket();

        String key = s3FileStorage.upload(file(originalName), "profile");

        // UUID 뒤에 허용된 확장자만 붙고, 그 외 문자는 key에 남지 않는다
        assertThat(key).matches("profile/[0-9a-f-]{36}" + Pattern.quote(expectedSuffix));
    }

    @Test
    @DisplayName("업로드: 같은 파일을 두 번 올려도 key가 겹치지 않는다")
    void upload_keysAreUnique() {
        injectBucket();

        String first = s3FileStorage.upload(file("photo.png"), "profile");
        String second = s3FileStorage.upload(file("photo.png"), "profile");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("업로드: 버킷·Content-Type·길이를 요청에 담는다")
    void upload_putsRequestMetadata() {
        injectBucket();

        s3FileStorage.upload(file("photo.png"), "ai-grade");

        PutObjectRequest request = capturePut();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentLength()).isEqualTo(3L);
        assertThat(request.key()).startsWith("ai-grade/");
    }

    @Test
    @DisplayName("삭제: 지정한 버킷과 key로 삭제를 요청한다")
    void delete_requestsCorrectObject() {
        injectBucket();

        s3FileStorage.delete("profile/some-key.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        then(s3Client).should().deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("profile/some-key.png");
    }
}
