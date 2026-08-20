package com.pokade.domain.point.service;

import com.pokade.domain.point.entity.PointTransaction;
import com.pokade.domain.point.entity.PointTransactionType;
import com.pokade.domain.point.repository.PointTransactionRepository;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserRepository userRepository;
    private final PointTransactionRepository pointTransactionRepository;

    // 포인트 충전 - 토스페이먼츠 결제 승인을 서버가 직접 검증한 뒤에만 호출한다. 충전 후 잔액을 반환한다.
    @Transactional
    public int charge(Long userId, int amount) {
        User user = findUserWithLock(userId);
        user.chargePoints(amount);

        pointTransactionRepository.save(PointTransaction.builder()
                .userId(userId)
                .type(PointTransactionType.CHARGE)
                .amount(amount)
                .balanceAfter(user.getPointBalance())
                .build());

        return user.getPointBalance();
    }

    // 포인트 사용(매물 구매 등) - 잔액이 모자라면 BusinessException(INSUFFICIENT_POINT_BALANCE). 차감 후 잔액을 반환한다.
    @Transactional
    public int use(Long userId, int amount, Long relatedTradeId) {
        User user = findUserWithLock(userId);
        user.deductPoints(amount);

        pointTransactionRepository.save(PointTransaction.builder()
                .userId(userId)
                .type(PointTransactionType.USE)
                .amount(amount)
                .balanceAfter(user.getPointBalance())
                .relatedTradeId(relatedTradeId)
                .build());

        return user.getPointBalance();
    }

    // 포인트 환불(거래 취소 등) - 이전에 차감된 포인트를 되돌린다. charge()와 달리 관련 거래 id를 이력에 남긴다.
    @Transactional
    public int refund(Long userId, int amount, Long relatedTradeId) {
        User user = findUserWithLock(userId);
        user.chargePoints(amount);

        pointTransactionRepository.save(PointTransaction.builder()
                .userId(userId)
                .type(PointTransactionType.REFUND)
                .amount(amount)
                .balanceAfter(user.getPointBalance())
                .relatedTradeId(relatedTradeId)
                .build());

        return user.getPointBalance();
    }

    // 동시 요청이 같은 유저의 잔액을 함께 읽고-갱신해도 갱신유실/초과차감이 없도록 비관적 쓰기 락으로 조회한다.
    private User findUserWithLock(Long userId) {
        return userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
