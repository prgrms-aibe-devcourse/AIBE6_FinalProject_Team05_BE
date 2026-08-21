-- #300: 카드 검색이 메인 기능인데 cards 테이블에 검색/정렬 컬럼 인덱스가 전혀 없어 풀스캔으로 동작 중이었다.
-- 로컬에서 10만 행 합성 데이터로 EXPLAIN ANALYZE 실측 후 결정한 구성:
--
-- 1) 정렬 컬럼(synced_at/name/view_count) + id 복합 인덱스가 핵심이다 - CardRepository의 3개 검색
--    쿼리는 항상 "ORDER BY <정렬컬럼> ... LIMIT n" 형태라, 이 인덱스를 정렬된 순서로 그대로 훑으며
--    남은 필터를 Filter로 걸러내다 LIMIT만큼 채우면 멈추는 전략을 플래너가 선택한다(측정상 10ms대 →
--    0.1ms 미만). id를 항상 두 번째 컬럼으로 둔 이유는 쿼리의 실제 ORDER BY(`c.id DESC`/`c.id ASC`
--    타이브레이커)와 완전히 일치시켜야 인덱스만으로 정렬이 끝나기 때문이다.
-- 2) rarity/language_code/expansion_id 단일 컬럼 인덱스는 위 정렬 인덱스로 커버되지 않는 경우
--    (예: 필터 값 자체가 매우 희소한 경우) 플래너가 대안으로 선택한다 - 실측에서 확인.
-- 3) types는 text[] 배열 컬럼이라 B-tree가 아니라 GIN이 필요하다. 단, GIN은 `&&`(overlap)/`@>` 연산자만
--    지원하고 CardRepository의 기존 `EXISTS (SELECT 1 FROM unnest(c.types) t WHERE val IN (:types))`
--    패턴은 인식하지 못해 이 인덱스만 추가하면 그대로 Seq Scan이 유지된다(실측 확인) - 그래서 이번
--    변경에서 해당 쿼리를 `c.types && CAST(:types AS text[])`로 함께 재작성했다(CardRepository.java).
--    types 바인딩은 `List<String>`이 아니라 `String[]`이어야 한다 - Hibernate가 List 파라미터를
--    IN절처럼 `(?,?)` 튜플로 확장해버려 ARRAY/overlap 구성이 깨지는 걸 실측으로 확인했다.
CREATE INDEX IF NOT EXISTS idx_cards_synced_at_id ON cards(synced_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_cards_name_id ON cards(name ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_cards_view_count_id ON cards(view_count DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_cards_rarity ON cards(rarity);
CREATE INDEX IF NOT EXISTS idx_cards_language_code ON cards(language_code);
CREATE INDEX IF NOT EXISTS idx_cards_expansion_id ON cards(expansion_id);
CREATE INDEX IF NOT EXISTS idx_cards_types_gin ON cards USING gin(types);
