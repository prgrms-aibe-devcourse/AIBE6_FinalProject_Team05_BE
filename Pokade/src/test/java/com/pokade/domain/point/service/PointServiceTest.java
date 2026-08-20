package com.pokade.domain.point.service;

import com.pokade.domain.point.entity.PointTransaction;
import com.pokade.domain.point.entity.PointTransactionType;
import com.pokade.domain.point.repository.PointTransactionRepository;
import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @InjectMocks
    private PointService pointService;

    private User user(int pointBalance) {
        return User.builder()
                .email("point-user@test.com")
                .nickname("tester")
                .role(Role.USER)
                .provider(Provider.LOCAL)
                .status(UserStatus.ACTIVE)
                .termsAgreedAt(LocalDateTime.now())
                .pointBalance(pointBalance)
                .build();
    }

    @Test
    @DisplayName("charge: 잠금 조회한 유저의 잔액을 충전하고 CHARGE 이력을 남긴 뒤 충전 후 잔액을 반환한다")
    void charge_addsBalanceAndSavesHistory() {
        User user = user(1000);
        given(userRepository.findByIdWithLock(1L)).willReturn(Optional.of(user));

        int result = pointService.charge(1L, 5000);

        assertThat(result).isEqualTo(6000);
        assertThat(user.getPointBalance()).isEqualTo(6000);

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        then(pointTransactionRepository).should().save(captor.capture());
        PointTransaction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo(PointTransactionType.CHARGE);
        assertThat(saved.getAmount()).isEqualTo(5000);
        assertThat(saved.getBalanceAfter()).isEqualTo(6000);
        assertThat(saved.getRelatedTradeId()).isNull();
    }

    @Test
    @DisplayName("use: 잔액이 충분하면 차감하고 relatedTradeId를 포함한 USE 이력을 남긴다")
    void use_sufficientBalance_deductsAndSavesHistory() {
        User user = user(10000);
        given(userRepository.findByIdWithLock(1L)).willReturn(Optional.of(user));

        int result = pointService.use(1L, 3000, 42L);

        assertThat(result).isEqualTo(7000);

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        then(pointTransactionRepository).should().save(captor.capture());
        PointTransaction saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(PointTransactionType.USE);
        assertThat(saved.getAmount()).isEqualTo(3000);
        assertThat(saved.getBalanceAfter()).isEqualTo(7000);
        assertThat(saved.getRelatedTradeId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("use: 잔액이 부족하면 INSUFFICIENT_POINT_BALANCE를 던지고 이력을 남기지 않는다")
    void use_insufficientBalance_throwsAndDoesNotSave() {
        User user = user(1000);
        given(userRepository.findByIdWithLock(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> pointService.use(1L, 2000, 42L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_POINT_BALANCE);

        assertThat(user.getPointBalance()).isEqualTo(1000);
        then(pointTransactionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("settle: 판매자 잔액에 거래 금액을 적립하고 relatedTradeId를 포함한 SETTLEMENT 이력을 남긴다")
    void settle_addsBalanceAndSavesHistory() {
        User seller = user(1000);
        given(userRepository.findByIdWithLock(1L)).willReturn(Optional.of(seller));

        int result = pointService.settle(1L, 10000, 42L);

        assertThat(result).isEqualTo(11000);
        assertThat(seller.getPointBalance()).isEqualTo(11000);

        ArgumentCaptor<PointTransaction> captor = ArgumentCaptor.forClass(PointTransaction.class);
        then(pointTransactionRepository).should().save(captor.capture());
        PointTransaction saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(PointTransactionType.SETTLEMENT);
        assertThat(saved.getAmount()).isEqualTo(10000);
        assertThat(saved.getBalanceAfter()).isEqualTo(11000);
        assertThat(saved.getRelatedTradeId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("존재하지 않는 유저에게 charge/use/settle을 호출하면 USER_NOT_FOUND를 던진다")
    void missingUser_throwsUserNotFound() {
        given(userRepository.findByIdWithLock(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> pointService.charge(999L, 1000))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> pointService.use(999L, 1000, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        assertThatThrownBy(() -> pointService.settle(999L, 1000, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
