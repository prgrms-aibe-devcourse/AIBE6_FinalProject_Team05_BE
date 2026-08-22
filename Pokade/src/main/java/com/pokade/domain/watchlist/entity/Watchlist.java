package com.pokade.domain.watchlist.entity;

import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "watchlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Watchlist {

    // #238: 목표가 상한. DTO의 @Max와 같은 값을 두 군데 적지 않도록 도메인 엔티티에 단일 정의한다.
    public static final int MAX_TARGET_PRICE = 100_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    // null이면 대표 변형 기준
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "target_buy_price")
    private Integer targetBuyPrice;

    @Column(name = "target_sell_price")
    private Integer targetSellPrice;

    @Column(name = "is_notified", nullable = false)
    private boolean isNotified;

    // #300: 이 카드(variant)에 매물이 없다가 새로 생겼을 때 알림을 이미 보냈는지 여부. isNotified(목표가
    // 알림)와 별개 플래그다 - 한 워치리스트가 두 종류 알림을 독립적으로 받을 수 있다.
    // WatchlistListingNotifiedResetProcessor 배치가 매물이 다시 소진된 걸 감지하면 false로 리셋해서
    // 다음 재입고 때 또 알릴 수 있게 한다.
    @Column(name = "listing_notified", nullable = false)
    private boolean listingNotified;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Watchlist(Long userId, Long cardId, Long variantId, Integer targetBuyPrice, Integer targetSellPrice) {
        this.userId = userId;
        this.cardId = cardId;
        this.variantId = variantId;
        this.targetBuyPrice = targetBuyPrice;
        this.targetSellPrice = targetSellPrice;
        this.isNotified = false;
        this.listingNotified = false;
    }

    public void markAsNotified() {
        this.isNotified = true;
    }

    // #300: 매물 재입고 알림을 보냈음을 표시한다. markAsNotified()(목표가 알림)와 독립적으로 동작한다.
    public void markAsListingNotified() {
        this.listingNotified = true;
    }

    // #300: 매물이 다시 소진돼(활성 매물 0개) 다음 재입고 때 또 알릴 수 있도록 리셋한다.
    // WatchlistListingNotifiedResetProcessor 배치 전용 - resetNotification()(목표가 알림)과는 별개다.
    public void resetListingNotified() {
        this.listingNotified = false;
    }

    // updateTargetPrices()의 "가격이 실제로 바뀐 경우"에만 리셋하는 조건부 로직과 달리,
    // 조건 없이 무조건 리셋한다 - 내부적으로 같은 resetNotification()을 공유
    public void requestNotificationAgain() {
        resetNotification();
    }

    private void resetNotification() {
        this.isNotified = false;
    }

    // 안 보낸 필드(null)는 "기존 값 유지"로 해석한다. 목표가를 "지우는" 기능은 없다 - 한쪽만 지우는 것도,
    // 두 필드를 한꺼번에 지우는 것도 지원하지 않는다(관심 자체를 없애려면 DELETE). 그래서 null은 언제나
    // "변경 없음"이지 "삭제"가 아니다.
    public void updateTargetPrices(Integer targetBuyPrice, Integer targetSellPrice) {
        Integer resolvedBuyPrice = resolveUpdatedPrice(targetBuyPrice, this.targetBuyPrice);
        Integer resolvedSellPrice = resolveUpdatedPrice(targetSellPrice, this.targetSellPrice);
        boolean changed = !Objects.equals(this.targetBuyPrice, resolvedBuyPrice)
                || !Objects.equals(this.targetSellPrice, resolvedSellPrice);

        // 역전 검증은 "값이 실제로 바뀔 때"만 한다. 요청에 가격 필드가 왔는지로 판정하면, 기존과 똑같은
        // 값을 되보내는 no-op 수정이 검증에 걸려서 - 이 검증이 생기기 전에 저장된 역전 데이터를 가진
        // 사용자가 재알림조차 못 받게 된다(가격을 안 보내면 통과, 같은 값을 보내면 차단이라는 불일치).
        if (changed) {
            validateTargetPriceOrder(resolvedBuyPrice, resolvedSellPrice);
        }

        this.targetBuyPrice = resolvedBuyPrice;
        this.targetSellPrice = resolvedSellPrice;
        // 목표가가 실제로 바뀐 경우에만 isNotified를 리셋한다 - 이미 알림이 간 목표가를 그대로 재저장하는
        // no-op 수정에서는 배치가 불필요하게 재알림을 보내지 않도록 한다.
        if (changed) {
            resetNotification();
        }
    }

    // #238: 목표 구매가가 판매가 이상이면 두 목표가가 한 체결가에 동시에 걸려 알림이 의미를 잃는다.
    // 한쪽만 설정된 경우는 애초에 역전이 성립하지 않으므로 통과시킨다.
    // 등록 경로(WatchlistService.addWatchlist)는 빌더로 새 인스턴스를 만들어 updateTargetPrices()를
    // 거치지 않으므로, 같은 규칙을 쓰도록 이 메서드를 직접 호출한다.
    public static void validateTargetPriceOrder(Integer targetBuyPrice, Integer targetSellPrice) {
        if (targetBuyPrice == null || targetSellPrice == null) {
            return;
        }
        if (targetBuyPrice >= targetSellPrice) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_PRICE_RANGE);
        }
    }

    private static Integer resolveUpdatedPrice(Integer requested, Integer current) {
        return requested != null ? requested : current;
    }
}
