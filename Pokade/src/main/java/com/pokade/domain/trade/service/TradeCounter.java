package com.pokade.domain.trade.service;

import com.pokade.domain.trade.entity.TradeStatus;
import com.pokade.domain.trade.repository.TradeRepository;
import com.pokade.global.port.TradeCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeCounter implements TradeCountPort {

    private final TradeRepository tradeRepository;

    // 확정된 거래 수를 센다
    @Override
    public long countCompletedTrades(Long userId) {
        return tradeRepository.countByParticipantIdAndStatus(userId, TradeStatus.COMPLETED);
    }
}
