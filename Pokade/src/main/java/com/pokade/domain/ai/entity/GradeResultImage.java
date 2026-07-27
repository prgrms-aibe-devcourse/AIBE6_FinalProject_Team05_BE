package com.pokade.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "grade_result_images")
@Getter
@NoArgsConstructor
public class GradeResultImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade_result_id", nullable = false)
    private Long gradeResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", length = 20)
    private PhotoType photoType;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Builder
    public GradeResultImage(Long gradeResultId, PhotoType photoType, String imageUrl) {
        this.gradeResultId = gradeResultId;
        this.photoType = photoType;
        this.imageUrl = imageUrl;
    }
}
