package com.pokade.domain.admin.service;

import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryCategory;
import com.pokade.domain.inquiry.entity.InquiryImage;
import com.pokade.domain.inquiry.entity.InquiryStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

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

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    @DisplayName("카테고리 없이 조회하면 전체 문의를 최신순 페이지로 이미지와 함께 응답으로 변환한다")
    void getInquiries_withoutCategory_returnsAllPaged() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").category(InquiryCategory.ETC).build();
        given(inquiryRepository.findAllByOrderByCreatedAtDesc(pageable))
                .willReturn(new PageImpl<>(List.of(inquiry), pageable, 1));
        given(inquiryImageRepository.findByInquiryIdInOrderByIdAsc(any())).willReturn(List.of());

        Page<InquiryResponse> responses = adminInquiryService.getInquiries(null, pageable);

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).title()).isEqualTo("제목");
        assertThat(responses.getContent().get(0).imageUrls()).isEmpty();
        then(inquiryRepository).should(never()).findByCategoryOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("카테고리를 지정하면 해당 카테고리만 필터링된 조회 메서드를 사용한다")
    void getInquiries_withCategory_filtersByCategory() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("결제 문의").content("내용").category(InquiryCategory.PAYMENT).build();
        given(inquiryRepository.findByCategoryOrderByCreatedAtDesc(InquiryCategory.PAYMENT, pageable))
                .willReturn(new PageImpl<>(List.of(inquiry), pageable, 1));
        given(inquiryImageRepository.findByInquiryIdInOrderByIdAsc(any())).willReturn(List.of());

        Page<InquiryResponse> responses = adminInquiryService.getInquiries(InquiryCategory.PAYMENT, pageable);

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).category()).isEqualTo(InquiryCategory.PAYMENT);
        then(inquiryRepository).should(never()).findAllByOrderByCreatedAtDesc(any());
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

    @Test
    @DisplayName("상태를 변경하면 엔티티에 반영되고 변경된 상태로 응답한다")
    void updateStatus_changesStatus() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").category(InquiryCategory.ETC).build();
        given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiry));
        given(inquiryImageRepository.findByInquiryIdOrderByIdAsc(1L)).willReturn(List.of());

        InquiryResponse response = adminInquiryService.updateStatus(1L, InquiryStatus.HANDLED);

        assertThat(response.status()).isEqualTo(InquiryStatus.HANDLED);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.HANDLED);
    }

    @Test
    @DisplayName("존재하지 않는 문의의 상태를 변경하려 하면 BusinessException(INQUIRY_NOT_FOUND)을 던진다")
    void updateStatus_notFound_throws() {
        given(inquiryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminInquiryService.updateStatus(999L, InquiryStatus.HANDLED))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INQUIRY_NOT_FOUND);
    }
}
