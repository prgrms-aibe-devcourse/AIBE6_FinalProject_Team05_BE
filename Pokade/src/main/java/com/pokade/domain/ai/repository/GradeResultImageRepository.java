package com.pokade.domain.ai.repository;

import com.pokade.domain.ai.entity.GradeResultImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeResultImageRepository extends JpaRepository<GradeResultImage, Long> {

    List<GradeResultImage> findByGradeResultId(Long gradeResultId);
}
