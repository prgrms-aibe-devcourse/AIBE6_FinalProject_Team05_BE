package com.pokade.domain.report.dto;

import com.pokade.domain.report.entity.Report;
import com.pokade.domain.report.entity.ReportStatus;
import com.pokade.domain.report.entity.ReportTargetType;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        Long reporterId,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt
) {

    public static ReportResponse of(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReporterId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
