-- 동일 이미지(6장 SHA-256 해시)로 재요청 시 Vision API를 다시 호출하지 않고 이전 SUCCESS 결과를
-- 그대로 재사용하기 위한 컬럼. 같은 카드 사진인데 호출마다 등급이 미세하게 달라지는(S/A 왔다갔다)
-- 비일관성 문제를 원천적으로 막는다. 기존 행은 해시가 없어 NULL로 남고, 캐시 조회 대상이 아니므로
-- NOT NULL 제약은 걸지 않는다.
ALTER TABLE grade_results ADD COLUMN IF NOT EXISTS image_hash VARCHAR(64);

-- 캐시 조회(userId + imageHash + SUCCESS)가 매 요청마다 발생하므로 인덱스가 필요하다.
CREATE INDEX IF NOT EXISTS idx_grade_results_user_id_image_hash ON grade_results (user_id, image_hash);
