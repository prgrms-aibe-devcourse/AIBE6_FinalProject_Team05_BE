package com.pokade.global.infra.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * 버킷이 프라이빗이라 고정 공개 URL이 없다.
 * upload()는 S3 key만 반환하고, 실제 조회 시점에 generatePresignedUrl()로 임시 URL을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class S3FileStorage {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${pokade.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String folder) {
        String key = folder + "/" + UUID.randomUUID() + extensionOf(file);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("S3 업로드 실패 (folder=" + folder + ")", e);
        }
        return key;
    }

    public String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // 객체 메타데이터만 조회한다 (본문은 받지 않음) - ETag 비교용
    public HeadObjectResponse head(String key) {
        return s3Client.headObject(
                HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

    // 객체 본문 스트림을 연다 - 호출자가 닫아야 한다
    public ResponseInputStream<GetObjectResponse> openStream(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

    // 객체를 삭제한다
    public void delete(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

    // 원본 파일명은 사용자 입력이라 개인정보가 섞일 수 있어 key에 넣지 않고 확장자만 취한다.
    private String extensionOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        int dot = (name == null) ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? "." + ext : "";
    }

}
