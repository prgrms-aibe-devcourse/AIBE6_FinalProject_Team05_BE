package com.pokade.domain.trade.repository;

import com.pokade.domain.trade.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTradeId(Long tradeId);
}
