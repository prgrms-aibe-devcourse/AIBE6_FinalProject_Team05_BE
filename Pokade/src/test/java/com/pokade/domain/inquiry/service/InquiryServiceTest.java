package com.pokade.domain.inquiry.service;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;
    @Mock
    private InquiryImageRepository inquiryImageRepository;
    @Mock
    private S3FileStorage s3FileStorage;

    @InjectMocks
    private InquiryService inquiryService;

    private MultipartFile png(long size) {
        return new MockMultipartFile("images", "a.png", "image/png", new byte[(int) size]);
    }

    @Test
    @DisplayName("문의를 작성하면 작성자 id와 카테고리로 저장하고 응답으로 돌려준다")
    void createInquiry_savesWithUserId() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.PAYMENT, "제목", "내용");

        InquiryResponse response = inquiryService.createInquiry(1L, request, null);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        then(inquiryRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getTitle()).isEqualTo("제목");
        assertThat(captor.getValue().getContent()).isEqualTo("내용");
        assertThat(captor.getValue().getCategory()).isEqualTo(InquiryCategory.PAYMENT);
        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.imageUrls()).isEmpty();
        then(s3FileStorage).should(never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("문의 작성: 첨부 이미지는 S3에 업로드하고 key를 저장한 뒤 presigned URL로 응답한다")
    void createInquiry_uploadsImages() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.INFO, "제목", "내용");
        given(s3FileStorage.upload(any(), eq("inquiries"))).willReturn("inquiries/key.png");
        given(s3FileStorage.generatePresignedUrl("inquiries/key.png")).willReturn("https://s3/presigned");

        InquiryResponse response = inquiryService.createInquiry(1L, request, List.of(png(1024)));

        then(inquiryImageRepository).should().save(any(InquiryImage.class));
        assertThat(response.imageUrls()).containsExactly("https://s3/presigned");
    }

    @Test
    @DisplayName("문의 작성: 이미지가 4장 이상이면 INQUIRY_IMAGE_LIMIT_EXCEEDED, 아무것도 저장하지 않는다")
    void createInquiry_tooManyImages() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.ETC, "제목", "내용");
        List<MultipartFile> images = List.of(png(10), png(10), png(10), png(10));

        assertThatThrownBy(() -> inquiryService.createInquiry(1L, request, images))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_IMAGE_LIMIT_EXCEEDED);

        then(inquiryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("문의 작성: 5MB를 넘는 이미지가 있으면 FILE_TOO_LARGE")
    void createInquiry_imageTooLarge() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.ETC, "제목", "내용");
        List<MultipartFile> images = List.of(png(5 * 1024 * 1024 + 1));

        assertThatThrownBy(() -> inquiryService.createInquiry(1L, request, images))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("본인 문의 목록은 최신순으로 내려온 것을 이미지와 함께 응답으로 변환한다")
    void getMyInquiries_returnsMappedResponses() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").category(InquiryCategory.SECURITY).build();
        given(inquiryRepository.findByUserIdOrderByCreatedAtDesc(1L)).willReturn(List.of(inquiry));
        given(inquiryImageRepository.findByInquiryIdInOrderByIdAsc(any())).willReturn(List.of());

        List<InquiryResponse> responses = inquiryService.getMyInquiries(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("제목");
        assertThat(responses.get(0).category()).isEqualTo(InquiryCategory.SECURITY);
        assertThat(responses.get(0).imageUrls()).isEmpty();
    }
}
