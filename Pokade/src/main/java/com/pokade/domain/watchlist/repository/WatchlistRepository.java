package com.pokade.domain.watchlist.repository;

import com.pokade.domain.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 알림 생성 "권한"을 조건부 원자적 UPDATE로 선점한다. is_notified=false인 행만 갱신되므로,
    // 삭제됐거나 다른 트랜잭션/인스턴스가 이미 선점한 경우 0을 반환해 중복/유령 알림 생성을 막는다.
    // flushAutomatically=true가 필요한 이유: 이 bulk UPDATE는 Hibernate dirty-checking을 거치지 않고 DB의
    // 현재 커밋 상태만 보는데, 호출 전에 같은 트랜잭션에서 isNotified를 바꾼(예: requestNotificationAgain())
    // 아직 flush 안 된 변경이 있으면 그걸 못 보고 WHERE 조건이 틀리게 평가된다 - 먼저 flush해서 최신 상태로
    // 판정하게 한다. clearAutomatically는 절대 켜지 않는다 - 켜면 영속성 컨텍스트가 비워져서 이후 호출부의
    // watchlist.markAsNotified()가 detached 엔티티에 적용돼 추적되지 않는다.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Watchlist w SET w.isNotified = true WHERE w.id = :id AND w.isNotified = false")
    int markAsNotifiedIfNotYet(@Param("id") Long id);

    // 카드별 관심수(워치리스트 등록 수) 배치 조회 - 카드 수와 무관하게 쿼리 1회로 처리한다(CardRepository.findGradesByCardIds와 동일한 패턴).
    // 등록이 하나도 없는 카드는 결과 행 자체가 없으므로, 호출부에서 요청한 cardId 전체에 대해 0으로 채워야 한다.
    @Query("SELECT w.cardId AS cardId, COUNT(w) AS count FROM Watchlist w WHERE w.cardId IN :cardIds GROUP BY w.cardId")
    List<WatchlistCardCountView> countGroupedByCardIdIn(@Param("cardIds") List<Long> cardIds);

    interface WatchlistCardCountView {
        Long getCardId();
        Long getCount();
    }
}
