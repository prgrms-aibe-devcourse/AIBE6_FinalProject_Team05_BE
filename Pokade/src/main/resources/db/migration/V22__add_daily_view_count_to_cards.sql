-- 카드 상세의 "N회 조회"는 누적 view_count를 그대로 쓰고, 인기순 정렬(sort=popular)만 하루 단위로
-- 고정하기 위해 일간 카운터를 분리한다(#377). 매일 자정 DailyViewCountResetScheduler가 0으로 되돌린다.
ALTER TABLE cards ADD COLUMN IF NOT EXISTS daily_view_count INTEGER NOT NULL DEFAULT 0;

-- V10의 idx_cards_view_count_id와 같은 이유로 필수다: 인기순 쿼리는 항상
-- "ORDER BY <정렬컬럼> DESC, id DESC ... LIMIT n" 형태라 이 복합 인덱스가 없으면 풀스캔으로 회귀한다.
-- id를 두 번째 컬럼에 두는 것도 쿼리의 타이브레이커(c.id DESC)와 일치시켜 정렬을 인덱스로 끝내기 위함.
-- (V10의 idx_cards_view_count_id는 인기순이 유일한 소비처였어서 이 시점부터 사실상 미사용이 되지만,
--  이번 변경 범위를 좁게 두기 위해 삭제하지 않고 남겨둔다.)
CREATE INDEX IF NOT EXISTS idx_cards_daily_view_count_id ON cards(daily_view_count DESC, id DESC);
