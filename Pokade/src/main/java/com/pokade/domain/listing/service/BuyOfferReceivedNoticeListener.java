package com.pokade.domain.listing.service;

import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.entity.ListingStatus;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.notification.service.NotificationService;
import com.pokade.global.event.BuyOfferCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;

// 구매입찰이 등록되면 그 카드에 매물을 올려둔 판매자에게 알린다. 지금까지 입찰 관련 알림은 체결 시점
// (BUY_OFFER_MATCHED)에만 있었는데, 그건 이미 거래가 끝난 뒤라 판매자가 "이 가격에 팔지"를 판단할
// 기회 자체가 없었다.
//
// 위치가 price가 아니라 listing인 이유: WatchlistListingAvailableNoticeListener가 이벤트 발행자
// (listing)가 아니라 수신자 도메인(watchlist)에 있는 것과 같은 규칙이다. 이 알림의 수신자는
// 매물을 가진 판매자이므로 listing 쪽이 맞다.
//
// 결제 트랜잭션이 커밋된 뒤에만 동작해야 한다 - confirmBuyOfferPurchase()는 토스 승인과 포인트 차감이
// 이미 끝난 트랜잭션이라, 알림 저장이 그 트랜잭션에 얹히면 알림 쪽 DB 오류로 "결제는 됐는데 입찰은
// 없는" 상태가 만들어질 수 있다. AFTER_COMMIT으로 그 경로를 구조적으로 끊고, 리스너를 비트랜잭션으로
// 둬서 뒤늦게 터지는 예외까지 막는다(자세한 근거는 onBuyOfferCreated 위 주석 참고).
//
// 스코프 제한(의도적으로 이번 범위에서 제외):
// - variantId가 null인 매물은 수신자에서 빠진다. BuyOffer.variantId는 등록 시점에 대표 variant로
//   치환되지만(PriceService.readyBuyOffer) Listing.variantId는 nullable이고, findOrderbook의
//   l.variantId = :variantId는 SQL 특성상 NULL 행을 매칭하지 못한다. 워치리스트처럼 null을 치환해서
//   맞출 수도 있지만, 호가창(GET /api/listings/{cardId}/orderbook)이 이미 같은 쿼리로 그 매물을
//   숨기고 있어 알림만 다른 규칙을 쓰면 "알림 받고 들어갔는데 호가창에 내 매물이 없다"는 더 큰
//   불일치가 생긴다. 근본 해결은 매물 등록 시 variantId를 대표 판본으로 치환하는 것이고 별도 이슈다.
// - 팬아웃 인원에 상한이 없다. 매물이 많은 인기 카드면 입찰 1건에 수백 명이 알림을 받고, 같은 카드에
//   입찰이 연달아 들어오면 같은 판매자에게 계속 쌓인다. createInquiryReceivedNotification(관리자
//   팬아웃)도 같은 구조이고 현재 데이터 규모에서는 감내한다 - 상한/쿨다운은 별도 이슈다.
@Slf4j
@Component
@RequiredArgsConstructor
public class BuyOfferReceivedNoticeListener {

    private final ListingRepository listingRepository;
    private final CardRepository cardRepository;
    private final CardNameKoResolver cardNameKoResolver;
    private final NotificationService notificationService;

    // 본문 전체를 try/catch로 감싸는 이유: 이 메서드가 실패하면 그 예외가 AFTER_COMMIT 동기화를 타고
    // 바깥 commit() 호출부까지 전파된다. 그 지점은 이미 커밋이 끝난 confirmBuyOfferPurchase()라,
    // 토스 승인·포인트 차감·BuyOffer 저장은 전부 반영된 채로 POST /api/buy-offers/confirm-payment만
    // 500을 돌려주게 된다 - 구매자 눈에는 "결제 실패"로 보이지만 실제로는 과금과 입찰 등록이 모두
    // 성공한 상태다. 알림 하나 때문에 그 오해를 만들 이유가 없어 여기서 삼키고 로그만 남긴다.
    //
    // 이 메서드에 @Transactional을 붙이지 않는 것이 그 격리의 전제다. 트랜잭션 안에서 잡으면 DB 오류를
    // 삼켜도 트랜잭션이 이미 rollback-only로 표시돼 있어, 정상 반환한 뒤 프록시 커밋 시점에
    // UnexpectedRollbackException이 다시 밖으로 나간다 - catch가 트랜잭션 경계 바깥에 있어야
    // 격리가 실제로 성립한다.
    //
    // 트랜잭션 없이 둬도 안전한 근거:
    // - 이 리스너 자체에는 쓰기가 없다. 실제 쓰기는 전부
    //   NotificationService.createBuyOfferReceivedNotification 안에서 일어나고, 그 메서드가
    //   REQUIRES_NEW로 자기 트랜잭션을 직접 연다. AFTER_COMMIT 시점엔 이미 커밋된 트랜잭션의
    //   EntityManagerHolder가 아직 바인딩돼 있어 REQUIRED로는 거기 참여만 하고 커밋되지 않는데,
    //   그 함정을 리스너가 아니라 그쪽에서 막는다(자세한 근거는 해당 메서드 주석 참고).
    //   실패하면 그쪽 프록시가 롤백까지 마친 뒤 예외를 던지므로 아래 catch가 깨끗하게 받는다.
    // - 조회 두 건(findOrderbook, findById)은 트랜잭션 없이도 동작한다(각각 auto-commit).
    // - 트랜잭션 밖에서 Card를 만지지만 지연 로딩이 없어 LazyInitializationException 위험이 없다.
    //   cardNameKoResolver가 읽는 Card.nationalPokedexNumbers는 연관관계가 아니라
    //   @JdbcTypeCode(SqlTypes.ARRAY)로 매핑된 기본 컬럼이라 조회 시점에 이미 채워져 온다.
    //
    // 같은 AFTER_COMMIT 리스너인 WatchlistListingAvailableNoticeListener가 리스너 자체에 REQUIRES_NEW를
    // 두는 것과 갈리는 지점이 여기다 - 그쪽은 알림 생성 권한을 선점하는 조건부 UPDATE
    // (markListingNotifiedIfNotYet)와 엔티티 변경(markAsListingNotified, 더티 체킹)이라는 자체 쓰기가
    // 있어 리스너 레벨에 트랜잭션이 반드시 필요하다. 이 리스너에는 그런 쓰기가 없어 트랜잭션 경계를
    // 서비스 쪽으로 미룰 수 있고, 그 덕에 catch가 경계 바깥에 남아 예외 격리까지 함께 얻는다.
    //
    // fallbackExecution=true인 이유: @TransactionalEventListener는 발행 시점에 활성 트랜잭션이 없으면
    // 아무 로그도 남기지 않고 리스너를 조용히 건너뛴다. 정상 경로(confirmBuyOfferPurchase)는 항상
    // 트랜잭션 안이지만, 트랜잭션 없는 컨텍스트(단위 테스트, 이후 추가될 다른 호출부)에서 이벤트가
    // 발행되면 알림이 소리 없이 사라진다. 이 리스너는 트랜잭션에 얹히는 게 없으므로(위 근거 참고)
    // 그런 경우에도 그냥 실행되는 편이 낫다 - NotificationService.onNotificationPush와 같은 판단이다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBuyOfferCreated(BuyOfferCreatedEvent event) {
        try {
            notifySellers(event);
        } catch (Exception e) {
            // TradeService.notifyQuietly의 문구를 그대로 쓰지 않는다 - 그쪽은 거래 트랜잭션 안(REQUIRED)이라
            // "거래까지 함께 롤백될 수 있다"를 경고해야 하지만, 여기는 AFTER_COMMIT이라 결제와 입찰은
            // 이미 커밋이 끝나 안전하다. 실제로 잃는 것은 알림뿐이므로 그 사실만 정확히 적는다.
            log.error("구매입찰 등록 알림 발행 실패 - 결제와 입찰은 이미 커밋되어 그대로 유효하고 알림만 유실된다:"
                    + " buyOfferId={}, cardId={}", event.buyOfferId(), event.cardId(), e);
        }
    }

    private void notifySellers(BuyOfferCreatedEvent event) {
        // 매도 호가창과 같은 쿼리를 그대로 쓴다 - 이 입찰에 실제로 팔 수 있는 매물의 집합이 곧 알림
        // 대상이기 때문이다. 정지/탈퇴 판매자를 걸러내는 서브쿼리도 여기 이미 들어있어 따로 볼 필요가 없다.
        // grade가 null이면(등급 무관 입찰) 쿼리가 전 등급을 돌려준다 - 어떤 등급이든 팔 수 있다는
        // 뜻이므로 의도한 동작이다.
        List<Listing> listings = listingRepository.findOrderbook(
                event.cardId(), event.variantId(), ListingStatus.ACTIVE, event.grade());

        // 자기 자신 제외를 distinct보다 먼저 한다(결과는 같지만 중복 제거 대상이 줄어든다).
        // 입찰자가 같은 카드의 매물도 갖고 있을 수 있는데, 자기가 방금 건 입찰을 자기에게 알리는 건
        // 소음이다 - fulfillBuyOffer가 SELF_BUY_OFFER_NOT_ALLOWED로 막는 것과 같은 기준.
        // distinct는 한 판매자가 같은 카드에 매물을 여러 개 올린 경우 알림이 그 수만큼 가는 걸 막는다.
        List<Long> sellerIds = listings.stream()
                .map(Listing::getSellerId)
                .filter(Objects::nonNull)
                .filter(sellerId -> !sellerId.equals(event.buyerId()))
                .distinct()
                .toList();
        if (sellerIds.isEmpty()) {
            return;
        }

        Card card = cardRepository.findById(event.cardId()).orElse(null);
        if (card == null) {
            log.warn("구매입찰 등록 알림 대상 카드를 찾을 수 없어 스킵합니다: cardId={}, buyOfferId={}",
                    event.cardId(), event.buyOfferId());
            return;
        }
        String cardName = Objects.requireNonNullElse(cardNameKoResolver.resolve(card), card.getName());

        notificationService.createBuyOfferReceivedNotification(
                sellerIds, event.cardId(), cardName, event.price(), card);
    }
}
