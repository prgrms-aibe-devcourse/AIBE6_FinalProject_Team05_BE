package com.pokade.domain.admin.service;

import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.report.dto.ReportResponse;
import com.pokade.domain.report.entity.Report;
import com.pokade.domain.report.entity.ReportTargetType;
import com.pokade.domain.report.repository.ReportRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminListingServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private AdminListingService adminListingService;

    private Report reportOf(Long targetId, String reason) {
        return Report.builder()
                .targetType(ReportTargetType.LISTING)
                .targetId(targetId)
                .reporterId(999L)
                .reason(reason)
                .build();
    }

    @Test
    void 신고가_없으면_빈_목록을_반환한다() {
        given(reportRepository.findByTargetTypeOrderByCreatedAtDesc(ReportTargetType.LISTING))
                .willReturn(List.of());

        List<ReportResponse> responses = adminListingService.getListingReports();

        assertThat(responses).isEmpty();
    }

    @Test
    void 신고가_있으면_목록을_반환한다() {
        Report report = reportOf(1L, "허위 매물입니다");
        given(reportRepository.findByTargetTypeOrderByCreatedAtDesc(ReportTargetType.LISTING))
                .willReturn(List.of(report));

        List<ReportResponse> responses = adminListingService.getListingReports();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).targetId()).isEqualTo(1L);
        assertThat(responses.get(0).reason()).isEqualTo("허위 매물입니다");
    }

    @Test
    void 매물을_숨김_처리한다() {
        given(listingRepository.existsById(1L)).willReturn(true);
        given(listingRepository.hideIfNotAlreadyHidden(1L)).willReturn(1);

        adminListingService.hideListing(1L);

        verify(listingRepository).hideIfNotAlreadyHidden(1L);
    }

    @Test
    void 이미_숨김_처리된_매물이면_INVALID_LISTING_STATUS_예외가_발생한다() {
        given(listingRepository.existsById(1L)).willReturn(true);
        given(listingRepository.hideIfNotAlreadyHidden(1L)).willReturn(0);

        assertThatThrownBy(() -> adminListingService.hideListing(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_LISTING_STATUS);
    }

    @Test
    void 동시에_숨김_처리를_시도하면_하나만_성공하고_나머지는_INVALID_LISTING_STATUS_예외가_발생한다() {
        given(listingRepository.existsById(1L)).willReturn(true);
        // 원자적 조건부 UPDATE이므로, 동시에 두 요청이 들어와도 DB 행 잠금에 의해 하나만 1을 반환하고
        // 나머지는 0을 반환한다 — 여기서는 그 결과 계약(첫 호출 1, 이후 호출 0)만 검증한다.
        given(listingRepository.hideIfNotAlreadyHidden(1L)).willReturn(1, 0);

        adminListingService.hideListing(1L);
        assertThatThrownBy(() -> adminListingService.hideListing(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_LISTING_STATUS);
    }

    @Test
    void 존재하지_않는_매물을_숨김_처리하면_LISTING_NOT_FOUND_예외가_발생한다() {
        given(listingRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> adminListingService.hideListing(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LISTING_NOT_FOUND);

        verify(listingRepository, never()).hideIfNotAlreadyHidden(anyLong());
    }
}
