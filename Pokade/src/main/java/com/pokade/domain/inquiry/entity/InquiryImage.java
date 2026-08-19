package com.pokade.domain.inquiry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inquiry_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    // S3 key를 저장한다 (버킷이 프라이빗이라 실제 URL은 조회 시점에 presigned URL로 발급) - S3FileStorage 참고
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Builder
    public InquiryImage(Long inquiryId, String imageUrl) {
        this.inquiryId = inquiryId;
        this.imageUrl = imageUrl;
    }
}
