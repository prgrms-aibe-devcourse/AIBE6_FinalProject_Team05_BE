-- 카드 이름 오타 허용 검색(#187)을 위해 pg_trgm 확장과 트라이그램 GIN 인덱스를 추가한다.
--
-- 주의: CardQueryService의 폴백 쿼리(similarity(col, :keyword) >= :threshold)는 함수 호출 형태의
-- 조건이라 플래너가 이 GIN 인덱스를 직접 타지 않는다(인덱스로 가속하려면 %/<-> 연산자를 써야 하는데,
-- 그건 pg_trgm.similarity_threshold를 세션 단위로 바꿔야 해서 커넥션 풀에 값이 새어나갈 위험이 있어
-- 이번 범위에서는 피했다). 폴백은 "정확 검색 0건일 때만" 도는 저빈도 경로라 순차 스캔이어도 현재
-- 테이블 크기(수천 행)에서는 감내할 만하다고 보고 시작한다 - 카드 수가 크게 늘면 재검토 필요.
-- 이 인덱스는 대신 기존 LIKE/ILIKE 부분일치 검색(findByNameContainingIgnoreCase 등)이 테이블이
-- 커졌을 때 순차 스캔에 의존하지 않도록 미리 깔아두는 목적이 크다.
--
-- pg_trgm은 PostgreSQL 13+부터 "trusted extension"이라 슈퍼유저 없이도 스키마 생성 권한만으로
-- 설치 가능하다(관리형 Postgres에서도 대부분 허용). fuzzystrmatch(Levenshtein)는 스파이크 테스트
-- 결과 짧은 한글 이름에서 pg_trgm과 동일한 동점 문제를 겪으면서 슈퍼유저 권한 리스크만 추가돼
-- 이번 범위에서 제외했다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_cards_name_trgm ON cards USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_pokedex_ko_names_name_ko_trgm ON pokedex_ko_names USING gin (name_ko gin_trgm_ops);
