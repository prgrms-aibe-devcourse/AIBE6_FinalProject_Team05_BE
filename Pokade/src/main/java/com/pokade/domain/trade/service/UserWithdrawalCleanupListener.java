package com.pokade.domain.trade.service;

import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// 회원탈퇴가 확정된 유저의 매물/거래를 정리한다. 탈퇴 확정 트랜잭션이 커밋된 뒤에만 동작해야
// (그새 철회되는 등으로) 탈퇴 자체가 롤백된 경우 매물/거래를 잘못 취소하지 않는다.
@Component
@RequiredArgsConstructor
public class UserWithdrawalCleanupListener {

    private static final List<TradeStatus> UNSETTLED_STATUSES = List.of(TradeStatus.PENDING, TradeStatus.MATCHED);

    private final ListingRepository listingRepository;
    private final TradeRepository tradeRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onUserWithdrawn(UserWithdrawnEvent event) {
        Long userId = event.userId();

        List<Listing> activeListings = listingRepository.findBySellerIdAndStatus(userId, ListingStatus.ACTIVE);
        activeListings.forEach(Listing::cancel);

        List<Trade> unsettledTrades = tradeRepository.findByParticipantIdAndStatusIn(userId, UNSETTLED_STATUSES);
        unsettledTrades.forEach(Trade::cancel);
    }
}
