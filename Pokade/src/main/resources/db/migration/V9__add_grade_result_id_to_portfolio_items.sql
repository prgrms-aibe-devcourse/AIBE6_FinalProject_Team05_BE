-- AI 등급 진단 결과에서 바로 도감(portfolio)에 등록할 수 있게 되어(#193),
-- 어떤 진단 결과로부터 등록된 항목인지 추적하기 위한 컬럼. UNIQUE로 동일 진단 결과의 중복 등록을 막는다.
ALTER TABLE portfolio_items ADD COLUMN IF NOT EXISTS grade_result_id BIGINT UNIQUE REFERENCES grade_results(id);
