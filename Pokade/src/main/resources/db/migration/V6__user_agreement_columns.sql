-- V3(user_agreements 이관)가 전제로 하는 컬럼들을 먼저 만든다.
-- 가입 시점에 항상 동의한 것으로 하드코딩해 왔으므로 NOT NULL DEFAULT로 채워도 기존 데이터와 불일치가 없다.

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_agreed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

COMMIT;
