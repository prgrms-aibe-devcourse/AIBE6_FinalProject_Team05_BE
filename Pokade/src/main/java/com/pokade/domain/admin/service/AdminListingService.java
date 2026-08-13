package com.pokade.domain.admin.service;

import com.pokade.domain.report.dto.ReportResponse;
import com.pokade.domain.report.entity.ReportTargetType;
import com.pokade.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminListingService {

    private final ReportRepository reportRepository;

    // FR-ADMIN-01: 신고된 매물 목록 조회 (신고 없으면 빈 목록)
    public List<ReportResponse> getListingReports() {
        return reportRepository.findByTargetTypeOrderByCreatedAtDesc(ReportTargetType.LISTING)
                .stream()
                .map(ReportResponse::of)
                .toList();
    }
}
