package com.pokade.domain.report.repository;

import com.pokade.domain.report.entity.Report;
import com.pokade.domain.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType);
}
