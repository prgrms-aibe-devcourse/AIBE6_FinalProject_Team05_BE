package com.pokade.domain.inquiry.service;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @InjectMocks
    private InquiryService inquiryService;

    @Test
    @DisplayName("문의를 작성하면 작성자 id로 저장하고 응답으로 돌려준다")
    void createInquiry_savesWithUserId() {
        InquiryCreateRequest request = new InquiryCreateRequest("제목", "내용");

        InquiryResponse response = inquiryService.createInquiry(1L, request);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        then(inquiryRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getTitle()).isEqualTo("제목");
        assertThat(captor.getValue().getContent()).isEqualTo("내용");
        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("본인 문의 목록은 최신순으로 내려온 것을 그대로 응답으로 변환한다")
    void getMyInquiries_returnsMappedResponses() {
        Inquiry inquiry = Inquiry.builder().userId(1L).title("제목").content("내용").build();
        given(inquiryRepository.findByUserIdOrderByCreatedAtDesc(1L)).willReturn(List.of(inquiry));

        List<InquiryResponse> responses = inquiryService.getMyInquiries(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("제목");
    }
}
