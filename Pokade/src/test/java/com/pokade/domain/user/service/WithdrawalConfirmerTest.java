package com.pokade.domain.user.service;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import com.pokade.domain.user.repository.UserRepository;
import com.pokade.domain.user.support.AnonymizationTokenGenerator;
import com.pokade.global.event.UserWithdrawnEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WithdrawalConfirmerTest {

    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AnonymizationTokenGenerator anonTokenGenerator;
    @InjectMocks WithdrawalConfirmer withdrawalConfirmer;

    private User userWithStatus(long id, UserStatus status) {
        return User.builder()
                .id(id).email("bye@pokade.com").password("ENCODED_PW")
                .nickname("바이").role(Role.USER).provider(Provider.LOCAL)
                .status(status).pointBalance(0)
                .build();
    }

    @Test
    @DisplayName("유예(WITHDRAWAL_PENDING)면 DELETED 익명화 + 이벤트 발행 후 true를 반환한다")
    void confirm_pending_confirmsAndReturnsTrue() {
        User user = userWithStatus(2L, UserStatus.WITHDRAWAL_PENDING);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(anonTokenGenerator.generate()).willReturn("a1b2c3d4e5f6"); // 12자리 hex 스텁

        boolean result = withdrawalConfirmer.confirm(2L);

        assertThat(result).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).matches("deleted_[0-9a-f]{12}@pokade\\.invalid");
        assertThat(user.getNickname()).matches("deleted_[0-9a-f]{12}");
        assertThat(user.getPassword()).isNull();
        ArgumentCaptor<UserWithdrawnEvent> captor = ArgumentCaptor.forClass(UserWithdrawnEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(2L); // payload userId까지 검증
    }

    @Test
    @DisplayName("그새 철회돼 ACTIVE면 확정하지 않고 false를 반환한다(경합 보호·멱등)")
    void confirm_active_skipsAndReturnsFalse() {
        User user = userWithStatus(2L, UserStatus.ACTIVE);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        boolean result = withdrawalConfirmer.confirm(2L);

        assertThat(result).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE); // 상태 안 바뀜
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 확정된 DELETED면 다시 확정하지 않고 false를 반환한다(배치 재실행 안전)")
    void confirm_deleted_skipsAndReturnsFalse() {
        User user = userWithStatus(2L, UserStatus.DELETED);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        boolean result = withdrawalConfirmer.confirm(2L);

        assertThat(result).isFalse();
        then(eventPublisher).should(never()).publishEvent(any());
    }
}
