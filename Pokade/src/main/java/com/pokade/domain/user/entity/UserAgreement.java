package com.pokade.domain.user.entity;

import com.pokade.domain.user.entity.type.AgreementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserAgreement {

    // 현재 약관 버전, 개정하면 이 값을 올리고, 기존 동의는 옛 버전으로 남아 재동의 대상 판별에 쓸 수 있다.
    public static final String CURRENT_VERSION = "1.0";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgreementType type;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Column(nullable = false, length = 20)
    private String version;

    // 동의 이력 한 건을 남긴다. 철회도 agreed=false인 새 행으로 기록해 이력을 덮이지 않는다.
    public static UserAgreement record(Long userId, AgreementType type, boolean agreed, LocalDateTime now) {
        return UserAgreement.builder()
                .userId(userId)
                .type(type)
                .agreed(agreed)
                .agreedAt(now)
                .version(CURRENT_VERSION)
                .build();
    }
}
