package com.pokade.domain.listing.service;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
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
