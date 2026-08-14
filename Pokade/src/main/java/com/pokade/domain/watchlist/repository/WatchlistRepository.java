package com.pokade.domain.watchlist.repository;

import com.pokade.domain.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByUserId(Long userId);

    boolean existsByUserIdAndCardId(Long userId, Long cardId);

    long countByUserId(Long userId);

    Optional<Watchlist> findByIdAndUserId(Long id, Long userId);

    List<Watchlist> findByIsNotifiedFalse();

    // 유저 단위 등록 직렬화용 트랜잭션 스코프 잠금(커밋/롤백 시 자동 해제). 동일 유저의 동시 addWatchlist() 요청을
    // 직렬화해 "중복 체크 + 20개 제한 체크 + 저장" 구간을 원자적으로 만든다. 다른 유저의 요청과는 경합하지 않는다.
    @Query(value = "SELECT pg_advisory_xact_lock(:userId)", nativeQuery = true)
    void acquireUserLock(@Param("userId") Long userId);
}
