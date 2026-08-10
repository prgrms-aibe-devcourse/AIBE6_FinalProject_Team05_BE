package com.pokade.domain.trade.service;

import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.listing.entity.Listing;
import com.pokade.domain.listing.repository.ListingRepository;
import com.pokade.domain.trade.dto.TradeCreateRequest;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.Trade;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.PaymentRepository;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.port.UserAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserAccessChecker userAccessChecker;

    @InjectMocks
    private TradeService tradeService;

    private Trade tradeOf(Long sellerId, Long buyerId) {
        Listing listing = Listing.builder()
                .cardId(1L)
                .sellerId(sellerId)
                .price(10000)
                .build();

        return Trade.builder()
                .listing(listing)
                .buyerId(buyerId)
                .price(10000)
                .build();
    }

    @Test
    void 즉시구매시_구매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.createTrade(200L, new TradeCreateRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(listingRepository, never()).findById(anyLong());
    }

    @Test
    void 즉시구매시_판매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(listingRepository.findById(1L)).willReturn(Optional.of(trade.getListing()));
        willDoNothing().given(userAccessChecker).assertWritable(200L);
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(100L);

        assertThatThrownBy(() -> tradeService.createTrade(200L, new TradeCreateRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(listingRepository, never()).markAsTrading(any());
    }

    @Test
    void 즉시구매시_구매자_판매자_모두_활성이면_거래가_생성된다() {
        Listing listing = tradeOf(100L, 200L).getListing();
        given(listingRepository.findById(1L)).willReturn(Optional.of(listing));
        given(listingRepository.markAsTrading(any())).willReturn(1);
        given(tradeRepository.save(any(Trade.class))).willAnswer(invocation -> invocation.getArgument(0));

        TradeResponse response = tradeService.createTrade(200L, new TradeCreateRequest(1L));

        assertThat(response.buyerId()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo(TradeStatus.PENDING);
    }

    @Test
    void 구매자_본인이_조회하면_거래정보를_반환한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.getTrade(200L, 1L);

        assertThat(response.buyerId()).isEqualTo(200L);
    }

    @Test
    void 판매자_본인이_조회하면_거래정보를_반환한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.getTrade(100L, 1L);

        assertThat(response.buyerId()).isEqualTo(200L);
    }

    @Test
    void 구매자도_판매자도_아니면_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.getTrade(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 존재하지_않는_거래를_조회하면_TRADE_NOT_FOUND_예외가_발생한다() {
        given(tradeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.getTrade(200L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 확정시_구매자_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.confirmTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeRepository, never()).findById(anyLong());
    }

    @Test
    void 구매자가_확정하면_거래상태가_COMPLETED로_바뀐다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.confirmTrade(200L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.COMPLETED);
    }

    @Test
    void 구매자가_아니면_확정시_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.confirmTrade(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 존재하지_않는_거래를_확정하면_TRADE_NOT_FOUND_예외가_발생한다() {
        given(tradeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.confirmTrade(200L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 이미_완료된_거래를_다시_확정하면_INVALID_TRADE_STATUS_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        trade.complete();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.confirmTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);
    }

    @Test
    void 취소시_액션_주체_계정이_비활성이면_ACCOUNT_NOT_ACTIVE_예외가_발생한다() {
        willThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE))
                .given(userAccessChecker).assertWritable(200L);

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(tradeRepository, never()).findById(anyLong());
    }

    @Test
    void 구매자가_취소하면_거래상태가_CANCELLED로_바뀐다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.cancelTrade(200L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.CANCELLED);
    }

    @Test
    void 판매자가_취소하면_거래상태가_CANCELLED로_바뀐다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        TradeResponse response = tradeService.cancelTrade(100L, 1L);

        assertThat(response.status()).isEqualTo(TradeStatus.CANCELLED);
    }

    @Test
    void 구매자도_판매자도_아니면_취소시_ACCESS_DENIED_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 존재하지_않는_거래를_취소하면_TRADE_NOT_FOUND_예외가_발생한다() {
        given(tradeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 이미_완료된_거래를_취소하면_INVALID_TRADE_STATUS_예외가_발생한다() {
        Trade trade = tradeOf(100L, 200L);
        trade.complete();
        given(tradeRepository.findById(1L)).willReturn(Optional.of(trade));

        assertThatThrownBy(() -> tradeService.cancelTrade(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TRADE_STATUS);
    }
}
