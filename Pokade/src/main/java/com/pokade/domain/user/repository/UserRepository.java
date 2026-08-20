package com.pokade.domain.user.repository;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.entity.type.Role;
import com.pokade.domain.user.entity.type.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    //관리자 회원 목록: 상태,역할 필터 + 이메일,닉네임 검색 + 페지이.
    // 선택 필터는 서비스에서 null이 아닌 값으로 정규화해 넘긴다 (미지정미연 전체 값 목록, 검색어 미지정이면 빈 문자열)
    @Query("SELECT u FROM User u "
            + "WHERE u.status IN :statuses "
            + "AND u.role IN :roles "
            + "AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "  OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> findForAdmin(@Param("statuses") List<UserStatus> statuses,
                            @Param("roles") List<Role> roles,
                            @Param("keyword") String keyword,
                            Pageable pageable);
}
