package com.pokade.domain.inquiry.service;

import com.pokade.domain.inquiry.dto.request.InquiryCreateRequest;
import com.pokade.domain.inquiry.dto.response.InquiryResponse;
import com.pokade.domain.inquiry.entity.Inquiry;
import com.pokade.domain.inquiry.entity.InquiryCategory;
import com.pokade.domain.inquiry.entity.InquiryImage;
import com.pokade.domain.inquiry.entity.InquiryStatus;
import com.pokade.domain.inquiry.repository.InquiryImageRepository;
import com.pokade.domain.inquiry.repository.InquiryRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
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
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InquiryService inquiryService;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47};

    private MultipartFile png(long size) {
        byte[] content = new byte[(int) size];
        System.arraycopy(PNG_SIGNATURE, 0, content, 0, Math.min(PNG_SIGNATURE.length, content.length));
        return new MockMultipartFile("images", "a.png", "image/png", content);
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
        assertThat(captor.getValue().getStatus()).isEqualTo(InquiryStatus.UNHANDLED);
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
    @DisplayName("문의 작성: Content-Type만 image/png고 실제 내용은 이미지가 아니면 UNSUPPORTED_IMAGE_TYPE")
    void createInquiry_spoofedContentType_rejected() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.ETC, "제목", "내용");
        MultipartFile fakeImage = new MockMultipartFile("images", "a.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> inquiryService.createInquiry(1L, request, List.of(fakeImage)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);

        then(inquiryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("문의 작성: 이미지 업로드 중 하나가 실패하면 이미 올라간 S3 객체를 정리하고 예외를 전파한다")
    void createInquiry_uploadFailsMidway_cleansUpUploadedObjects() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.ETC, "제목", "내용");
        MultipartFile first = png(10);
        MultipartFile second = png(10);
        given(s3FileStorage.upload(first, "inquiries")).willReturn("inquiries/first.png");
        given(s3FileStorage.upload(second, "inquiries")).willThrow(new RuntimeException("S3 장애"));

        assertThatThrownBy(() -> inquiryService.createInquiry(1L, request, List.of(first, second)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("S3 장애");

        then(s3FileStorage).should().delete("inquiries/first.png");
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

    // #392: 관리자 알림 팬아웃. 수신자 조회는 ACTIVE 관리자만 대상으로 해야 하고(DELETED/탈퇴대기
    // 계정에 알림이 쌓이지 않도록), 조회 결과를 그대로 id 목록으로 넘긴다.
    @Test
    @DisplayName("문의를 등록하면 활성 관리자 전원에게 알림이 발송된다")
    void createInquiry_notifiesEveryActiveAdmin() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.PAYMENT, "제목", "내용");
        // 관리자 mock의 스터빙을 given(...) 인자 안에서 하면 스터빙이 중첩돼
        // UnfinishedStubbingException이 난다 - 먼저 만들어 두고 그 값을 넘긴다.
        List<User> admins = List.of(adminWithId(10L), adminWithId(11L));
        given(userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).willReturn(admins);

        inquiryService.createInquiry(1L, request, List.of());

        then(notificationService).should()
                .createInquiryReceivedNotification(eq(List.of(10L, 11L)), any(), eq("제목"));
    }

    @Test
    @DisplayName("관리자가 한 명도 없어도 문의 등록은 성공한다")
    void createInquiry_withNoAdmin_stillSucceeds() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.PAYMENT, "제목", "내용");
        given(userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).willReturn(List.of());

        InquiryResponse response = inquiryService.createInquiry(1L, request, List.of());

        assertThat(response.title()).isEqualTo("제목");
        // 빈 목록이어도 호출 자체는 나가고, 걸러내는 책임은 NotificationService의 빈 목록 가드에 있다.
        then(notificationService).should().createInquiryReceivedNotification(eq(List.of()), any(), eq("제목"));
    }

    @Test
    @DisplayName("이미지 검증에서 걸리면 문의가 저장되지도, 관리자 알림이 나가지도 않는다")
    void createInquiry_whenImageInvalid_doesNotNotify() {
        InquiryCreateRequest request = new InquiryCreateRequest(InquiryCategory.PAYMENT, "제목", "내용");
        MultipartFile tooMany = png(10);

        assertThatThrownBy(() -> inquiryService.createInquiry(
                1L, request, List.of(tooMany, tooMany, tooMany, tooMany)))
                .isInstanceOf(BusinessException.class);

        then(inquiryRepository).should(never()).save(any());
        then(notificationService).should(never()).createInquiryReceivedNotification(any(), any(), any());
    }

    private User adminWithId(Long id) {
        User admin = org.mockito.Mockito.mock(User.class);
        given(admin.getId()).willReturn(id);
        return admin;
    }
}
