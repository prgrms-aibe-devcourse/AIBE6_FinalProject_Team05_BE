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
        // #392: 인앱 알림은 메일 try 밖에서 먼저 만든다. 같은 try 안에 넣으면 메일 서버가 죽었을 때
        // 인앱 알림까지 함께 유실돼, "메일이 안 가면 앱에도 안 뜬다"는 기존 문제가 그대로 남는다.
        // markStaleNoticeSent() 플래그는 여전히 메일 성공에만 걸리므로(아래), 메일만 실패한 매물은
        // 다음 배치에서 다시 대상이 되고 인앱 알림이 한 번 더 생길 수 있다 - 플래그가 채널별로
        // 나뉘어 있지 않은 데서 오는 한계이고, 이번 범위에서는 감수한다.
        notificationService.createListingStaleNotification(
                listing.getSellerId(), listing.getCardId(), listing.getId());
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
