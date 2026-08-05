package com.pokade.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

/**
 * 버킷이 프라이빗이라 고정 공개 URL이 없다.
 * upload()는 S3 key만 반환하고, 실제 조회 시점에 generatePresignedUrl()로 임시 URL을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class S3UploadService {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${pokade.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String folder) {
        String key = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("S3 업로드 실패: " + file.getOriginalFilename(), e);
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
}
