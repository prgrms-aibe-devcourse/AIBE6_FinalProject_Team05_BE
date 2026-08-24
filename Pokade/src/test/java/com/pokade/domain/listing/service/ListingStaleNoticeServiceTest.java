package com.pokade.domain.listing.service;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.infra.mail.MailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListingStaleNoticeServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailSender mailSender;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ListingStaleNoticeService listingStaleNoticeService;

    private Listing listingOf(Long sellerId) {
        return Listing.builder()
                .cardId(1L)
                .sellerId(sellerId)
                .price(10000)
                .build();
    }

    @Test
    void ACTIVE_상태와_30일_이전_cutoff로_대상을_조회한다() {
        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of());

        listingStaleNoticeService.sendStaleNotices();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(listingRepository).findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(
                eq(ListingStatus.ACTIVE), cutoffCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(30);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff,
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void 판매자를_찾으면_메일을_보내고_알림_발송_플래그를_갱신한다() {
        Listing listing = listingOf(1L);
        User seller = User.createLocalUser("seller@test.com", "hashed", "seller");

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(listing));
        given(userRepository.findById(1L)).willReturn(Optional.of(seller));

        listingStaleNoticeService.sendStaleNotices();

        verify(mailSender).send(eq("seller@test.com"), anyString(), anyString());
        assertThat(listing.isStaleNoticeSent()).isTrue();
    }

    @Test
    void 판매자를_찾을_수_없으면_알림을_보내지_않는다() {
        Listing listing = listingOf(999L);

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(listing));
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        listingStaleNoticeService.sendStaleNotices();

        verify(mailSender, never()).send(any(), any(), any());
        assertThat(listing.isStaleNoticeSent()).isFalse();
    }

    @Test
    void 메일_발송이_실패해도_나머지_매물은_계속_처리된다() {
        Listing failing = listingOf(1L);
        Listing succeeding = listingOf(2L);
        User seller1 = User.createLocalUser("seller1@test.com", "hashed", "seller1");
        User seller2 = User.createLocalUser("seller2@test.com", "hashed", "seller2");

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(failing, succeeding));
        given(userRepository.findById(1L)).willReturn(Optional.of(seller1));
        given(userRepository.findById(2L)).willReturn(Optional.of(seller2));
        doThrow(new RuntimeException("mail server down"))
                .when(mailSender).send(eq("seller1@test.com"), anyString(), anyString());

        listingStaleNoticeService.sendStaleNotices();

        assertThat(failing.isStaleNoticeSent()).isFalse();
        assertThat(succeeding.isStaleNoticeSent()).isTrue();
        verify(mailSender).send(eq("seller2@test.com"), anyString(), anyString());
    }

    @Test
    void 대상_매물마다_이메일과_인앱_알림이_함께_발송된다() {
        Listing listing = listingOf(1L);
        User seller = User.createLocalUser("seller@test.com", "hashed", "seller");

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(listing));
        given(userRepository.findById(1L)).willReturn(Optional.of(seller));

        listingStaleNoticeService.sendStaleNotices();

        verify(mailSender).send(eq("seller@test.com"), anyString(), anyString());
        verify(notificationService).createListingStaleNotification(
                eq(1L), eq(listing.getCardId()), eq(listing.getId()));
    }

    // #392: 인앱 알림 호출을 메일 try 블록 밖에 둔 이유를 고정하는 테스트다. try 안으로 들어가면
    // 메일 서버가 죽었을 때 인앱 알림까지 함께 유실되어, "메일이 안 가면 앱에도 안 뜬다"는
    // 원래 문제가 그대로 남는다.
    @Test
    void 메일_발송이_실패해도_인앱_알림은_발송된다() {
        Listing listing = listingOf(1L);
        User seller = User.createLocalUser("seller@test.com", "hashed", "seller");

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(listing));
        given(userRepository.findById(1L)).willReturn(Optional.of(seller));
        doThrow(new RuntimeException("mail server down"))
                .when(mailSender).send(anyString(), anyString(), anyString());

        listingStaleNoticeService.sendStaleNotices();

        verify(notificationService).createListingStaleNotification(
                eq(1L), eq(listing.getCardId()), eq(listing.getId()));
        // 메일 실패 시 플래그는 그대로 false - 기존 동작을 바꾸지 않았음을 함께 고정한다.
        assertThat(listing.isStaleNoticeSent()).isFalse();
    }

    @Test
    void 판매자를_찾을_수_없으면_인앱_알림도_보내지_않는다() {
        Listing listing = listingOf(999L);

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(listing));
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        listingStaleNoticeService.sendStaleNotices();

        verify(notificationService, never()).createListingStaleNotification(any(), any(), any());
    }

    // #392 회귀 방지: 앞선 커밋에서 인앱 알림 호출을 메일 try '밖'에 두는 바람에, 알림 생성이 실패하면
    // 예외가 for 루프를 뚫고 @Transactional인 sendStaleNotices() 밖으로 전파돼 배치 전체가 롤백됐다.
    // 아래 두 테스트가 "한 건이 실패해도 나머지는 계속 처리된다"는 원래 성질을 채널별로 고정한다.
    @Test
    void 인앱_알림_생성이_실패해도_같은_매물의_메일은_발송된다() {
        Listing listing = listingOf(1L);
        User seller = User.createLocalUser("seller@test.com", "hashed", "seller");

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(listing));
        given(userRepository.findById(1L)).willReturn(Optional.of(seller));
        doThrow(new RuntimeException("알림 저장 실패"))
                .when(notificationService).createListingStaleNotification(any(), any(), any());

        listingStaleNoticeService.sendStaleNotices();

        verify(mailSender).send(eq("seller@test.com"), anyString(), anyString());
        assertThat(listing.isStaleNoticeSent()).isTrue();
    }

    @Test
    void 인앱_알림_생성이_실패해도_나머지_매물은_계속_처리된다() {
        Listing failing = listingOf(1L);
        Listing succeeding = listingOf(2L);
        User seller1 = User.createLocalUser("seller1@test.com", "hashed", "seller1");
        User seller2 = User.createLocalUser("seller2@test.com", "hashed", "seller2");

        given(listingRepository.findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(any(), any()))
                .willReturn(List.of(failing, succeeding));
        given(userRepository.findById(1L)).willReturn(Optional.of(seller1));
        given(userRepository.findById(2L)).willReturn(Optional.of(seller2));
        doThrow(new RuntimeException("알림 저장 실패"))
                .when(notificationService).createListingStaleNotification(eq(1L), any(), any());

        listingStaleNoticeService.sendStaleNotices();

        // 예외가 배치 밖으로 전파됐다면 두 번째 매물은 아예 처리되지 않는다.
        verify(mailSender).send(eq("seller2@test.com"), anyString(), anyString());
        assertThat(succeeding.isStaleNoticeSent()).isTrue();
    }
}
