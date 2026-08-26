package com.pokade.domain.admin.service;

import com.pokade.domain.admin.dto.response.AdminTradeResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminTradeServiceTest {

    @Mock
    private TradeService tradeService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminTradeService adminTradeService;

    private User userOf(Long id, String nickname) {
        return User.builder().id(id).nickname(nickname).build();
    }

    private TradeResponse tradeResponseOf(Long id, Long buyerId, Long sellerId) {
        return new TradeResponse(
                id, 10L, buyerId, sellerId, 1L, "리자몽 ex", "리자몽", null, null, 10000,
                TradeStatus.SHIPPED_TO_PLATFORM,
                LocalDateTime.now(), null, null, null, null, null, null, null, LocalDateTime.now(), null);
    }

    @Test
    void 검수_배송_대기_목록_조회시_구매자_판매자_닉네임을_배치_조회해서_채운다() {
        TradeResponse trade1 = tradeResponseOf(1L, 100L, 200L);
        TradeResponse trade2 = tradeResponseOf(2L, 100L, 300L);
        given(tradeService.getPendingTrades()).willReturn(List.of(trade1, trade2));
        given(userRepository.findAllById(List.of(100L, 200L, 300L))).willReturn(List.of(
                userOf(100L, "구매자"), userOf(200L, "판매자1"), userOf(300L, "판매자2")));

        List<AdminTradeResponse> result = adminTradeService.getPendingTrades();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).buyerNickname()).isEqualTo("구매자");
        assertThat(result.get(0).sellerNickname()).isEqualTo("판매자1");
        assertThat(result.get(1).buyerNickname()).isEqualTo("구매자");
        assertThat(result.get(1).sellerNickname()).isEqualTo("판매자2");
    }

    @Test
    void 거래_상세_조회시_구매자_판매자_닉네임을_함께_반환한다() {
        TradeResponse trade = tradeResponseOf(1L, 100L, 200L);
        given(tradeService.getTradeForAdmin(1L)).willReturn(trade);
        given(userRepository.findAllById(List.of(100L, 200L))).willReturn(List.of(
                userOf(100L, "구매자"), userOf(200L, "판매자")));

        AdminTradeResponse result = adminTradeService.getTrade(1L);

        assertThat(result.buyerNickname()).isEqualTo("구매자");
        assertThat(result.sellerNickname()).isEqualTo("판매자");
        assertThat(result.cardNameKo()).isEqualTo("리자몽");
    }
}
