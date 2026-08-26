package com.pokade.domain.admin.service;

import com.pokade.domain.admin.dto.response.AdminTradeResponse;
import com.pokade.domain.trade.dto.TradeResponse;
import com.pokade.domain.trade.service.TradeService;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 관리자 거래 관리 화면 전용 - TradeService의 조회 결과에 판매자/구매자 닉네임을 붙인다.
// 실제 거래 조회/상태 전이 로직은 그대로 TradeService에 위임하고, 여기서는 닉네임 배치 조회만 담당한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTradeService {

    private final TradeService tradeService;
    private final UserRepository userRepository;

    // 검수/배송 대기 목록 - buyer/seller id를 한 번에 모아 배치 조회한다(목록 크기만큼 쿼리가 나가지 않도록).
    public List<AdminTradeResponse> getPendingTrades() {
        List<TradeResponse> trades = tradeService.getPendingTrades();
        Map<Long, String> nicknameByUserId = nicknameByUserId(trades);
        return trades.stream()
                .map(trade -> AdminTradeResponse.of(
                        trade,
                        nicknameByUserId.get(trade.buyerId()),
                        nicknameByUserId.get(trade.sellerId())))
                .toList();
    }

    public AdminTradeResponse getTrade(Long tradeId) {
        TradeResponse trade = tradeService.getTradeForAdmin(tradeId);
        Map<Long, String> nicknameByUserId = nicknameByUserId(List.of(trade));
        return AdminTradeResponse.of(
                trade, nicknameByUserId.get(trade.buyerId()), nicknameByUserId.get(trade.sellerId()));
    }

    private Map<Long, String> nicknameByUserId(List<TradeResponse> trades) {
        List<Long> userIds = trades.stream()
                .flatMap(t -> Stream.of(t.buyerId(), t.sellerId()))
                .distinct()
                .toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }
}
