package com.pokade.domain.user.entity;

import com.pokade.domain.user.entity.type.Provider;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    @Column(nullable = false, length = 20, unique = true)
    private String nickname;

    @Column(name = "nickname_changed_at")
    private LocalDateTime nicknameChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "point_balance", nullable = false)
    private Integer pointBalance;

    @Column(name = "deleted_at")
    private LocalDateTime deleted_At;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime created_At;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updated_At;

    public static User createLocalUser(String email, String encodedPassword, String nickname) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .status(UserStatus.PENDING)
                .termsAgreedAt(LocalDateTime.now())
                .marketingOptIn(false)
                .pointBalance(0)
                .build();
    }

    public void verifyEmail() {
        this.status = UserStatus.ACTIVE;
    }

    private static final int NICKNAME_CHANGE_COOLDOWN_DAYS = 30;

    // 닉네임을 마지막 변경 후 쿨 다운이 지났는지 여부
    public boolean canChangeNickname(LocalDateTime now) {
        return nicknameChangedAt == null || !now.isBefore(nicknameChangedAt.plusDays(NICKNAME_CHANGE_COOLDOWN_DAYS));
    }

    // 닉네임을 변경하고 변경 시각을 기록한다.
    public void changeNickname(String newNickname, LocalDateTime now) {
        this.nickname = newNickname;
        this.nicknameChangedAt = now;
    }

    // 자체(LOCAL) 가입 계정인지 여부
    public boolean isLocalUser() {
        return provider == Provider.LOCAL;
    }

    // 비밀번호를 변경한다 (인코딩된 값)
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

}
