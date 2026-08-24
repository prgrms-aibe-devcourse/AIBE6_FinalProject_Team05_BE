package com.pokade.domain.listing.service;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.infra.mail.MailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingStaleNoticeService {

    private static final int STALE_DAYS = 30;

    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final MailSender mailSender;
    // #392: 기존 이메일과 나란히 보내는 인앱 알림 - notify()에서만 쓴다.
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void sendStaleNotices() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(STALE_DAYS);
        List<Listing> staleListings = listingRepository
                .findByStatusAndStaleNoticeSentFalseAndCreatedAtBefore(ListingStatus.ACTIVE, cutoff);

        for (Listing listing : staleListings) {
            userRepository.findById(listing.getSellerId())
                    .ifPresent(seller -> notify(listing, seller));
        }
    }

    private void notify(Listing listing, User seller) {
        // #392: 인앱 알림과 이메일은 서로 독립이라 각자의 try로 감싼다. 한 채널이 죽었다고 다른 채널까지
        // 유실되면 "메일이 안 가면 앱에도 안 뜬다"는 원래 문제가 그대로 남는다.
        //
        // #392 후속: 두 호출을 모두 try로 감싸는 이유(회귀 수정) - 앞선 커밋에서 인앱 알림을 try '밖'에 두는 바람에,
        // 알림 생성이 실패하면 예외가 이 메서드와 for 루프를 뚫고 @Transactional인 sendStaleNotices() 밖으로
        // 전파됐다. 그러면 앞서 처리된 매물들의 markStaleNoticeSent() 플래그와 이미 만든 알림까지 전부
        // 롤백돼, 배치가 매물 단위로 격리돼 있던 성질(아래 catch가 원래 보장하던 것)이 깨진다.
        try {
            notificationService.createListingStaleNotification(
                    listing.getSellerId(), listing.getCardId(), listing.getId());
        } catch (Exception e) {
            log.warn("미체결 매물 인앱 알림 생성 실패: listingId={}, sellerId={}", listing.getId(), seller.getId(), e);
        }
        try {
            mailSender.send(
                    seller.getEmail(),
                    "[Pokade] 등록하신 매물이 30일간 판매되지 않았습니다",
                    "매물 #" + listing.getId() + "가 30일이 지나도록 판매되지 않았습니다. 가격을 확인해보세요."
            );
            listing.markStaleNoticeSent();
        } catch (Exception e) {
            log.warn("미체결 매물 알림 발송 실패: listingId={}, sellerId={}", listing.getId(), seller.getId(), e);
        }
    }
}
