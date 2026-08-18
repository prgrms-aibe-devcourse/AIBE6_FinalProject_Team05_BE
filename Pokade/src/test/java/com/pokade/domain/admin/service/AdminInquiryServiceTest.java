package com.pokade.domain.admin.service;

import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryCategory;
import com.pokade.domain.inquiry.entity.InquiryImage;
import com.pokade.domain.inquiry.repository.InquiryImageRepository;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;
    @Mock
    private InquiryImageRepository inquiryImageRepository;
    @Mock
    private S3FileStorage s3FileStorage;

    @InjectMocks
    private AdminInquiryService adminInquiryService;

    @Test
    @DisplayName("전체 문의 목록은 최신순으로 내려온 것을 이미지와 함께 응답으로 변환한다")
    void getInquiries_returnsMappedResponses() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").category(InquiryCategory.ETC).build();
        given(inquiryRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(inquiry));
        given(inquiryImageRepository.findByInquiryIdInOrderByIdAsc(any())).willReturn(List.of());

        List<InquiryResponse> responses = adminInquiryService.getInquiries();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("제목");
        assertThat(responses.get(0).imageUrls()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 문의를 조회하면 BusinessException(INQUIRY_NOT_FOUND)을 던진다")
    void getInquiry_notFound_throws() {
        given(inquiryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminInquiryService.getInquiry(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    @DisplayName("문의 상세 조회는 첨부 이미지를 presigned URL로 변환해 응답한다")
    void getInquiry_returnsPresignedImageUrls() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").category(InquiryCategory.SECURITY).build();
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiry));
        given(inquiryImageRepository.findByInquiryIdOrderByIdAsc(1L))
                .willReturn(List.of(InquiryImage.builder().inquiryId(1L).imageUrl("inquiries/key.png").build()));
        given(s3FileStorage.generatePresignedUrl("inquiries/key.png")).willReturn("https://s3/presigned");

        InquiryResponse response = adminInquiryService.getInquiry(1L);

        assertThat(response.imageUrls()).containsExactly("https://s3/presigned");
    }
}
