package com.pokade.domain.admin.service;

import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @InjectMocks
    private AdminInquiryService adminInquiryService;

    @Test
    @DisplayName("전체 문의 목록은 최신순으로 내려온 것을 그대로 응답으로 변환한다")
    void getInquiries_returnsMappedResponses() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").build();
        given(inquiryRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(inquiry));

        List<InquiryResponse> responses = adminInquiryService.getInquiries();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("존재하지 않는 문의를 조회하면 BusinessException(INQUIRY_NOT_FOUND)을 던진다")
    void getInquiry_notFound_throws() {
        given(inquiryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminInquiryService.getInquiry(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_NOT_FOUND);
    }
}
