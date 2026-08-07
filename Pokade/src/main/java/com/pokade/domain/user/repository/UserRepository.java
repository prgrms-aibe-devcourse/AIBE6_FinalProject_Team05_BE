package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmail(String email);


    List<User> findAllByStatusAndWithdrawalRequestedAtBefore(UserStatus status, LocalDateTime cutoff);

}
