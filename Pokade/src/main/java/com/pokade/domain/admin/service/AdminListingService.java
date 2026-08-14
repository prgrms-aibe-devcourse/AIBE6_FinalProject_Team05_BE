package com.pokade.domain.admin.service;

import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.report.dto.ReportResponse;
import com.pokade.domain.report.entity.ReportTargetType;
import com.pokade.domain.report.repository.ReportRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminListingService {

    private final ReportRepository reportRepository;
    private final ListingRepository listingRepository;

    // FR-ADMIN-01: 신고된 매물 목록 조회 (신고 없으면 빈 목록)
    public List<ReportResponse> getListingReports() {
        return reportRepository.findByTargetTypeOrderByCreatedAtDesc(ReportTargetType.LISTING)
                .stream()
                .map(ReportResponse::of)
                .toList();
    }

    // FR-ADMIN-02: 신고 검토 후 매물 숨김 처리.
    // 존재 확인과 상태 전환을 분리하되, 상태 전환 자체는 조건부 UPDATE(hideIfNotAlreadyHidden)로
    // 원자적으로 처리해 동시에 두 요청이 들어와도 하나만 성공하도록 한다.
    @Transactional
    public void hideListing(Long listingId) {
        if (!listingRepository.existsById(listingId)) {
            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        }

        int updated = listingRepository.hideIfNotAlreadyHidden(listingId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_LISTING_STATUS, "이미 숨김 처리된 매물입니다.");
        }
    }
}
