package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 포인트 충전/차감처럼 잔액을 읽고 바로 갱신하는 작업에서, 동시 요청이 같은 유저 행을 함께
    // 갱신해 갱신유실이 나지 않도록 비관적 쓰기 락을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);

}
