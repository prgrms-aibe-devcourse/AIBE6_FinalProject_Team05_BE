-- 회원 약관 동의 이력 테이블 도입. 지금까지는 users.terms_agreed_at / marketing_opt_in에
-- 값이 하드코딩되어 실제 동의를 받은 적이 없다(가입 요청에 동의 필드 자체가 없었다).
--
-- V2와 같은 전제로 작성한다 — 운영은 ddl-auto=validate, sql.init.mode=never이므로
-- 배포 담당자가 psql로 수동 1회 실행한다.
--
-- 실행 시점: 신버전이 뜨기 전까지 끝나 있기만 하면 되고, 얼마나 일찍 돌리든 무해하다.
--   develop 머지가 auto-promote → deploy로 이어져 자동 배포되므로, "배포 직전"에 끼워 넣을
--   틈이 없다. 따라서 PR을 머지하기 "전에" 미리 실행해 둔다. 이 스크립트는 추가와 제약 완화뿐이라
--   지금 돌려도 현재 운영 중인 구버전 앱의 동작이 달라지지 않는다.
--   반대로 이걸 건너뛰고 신버전이 뜨면 ddl-auto=validate가 user_agreements 부재를 잡아 기동에 실패한다.
--   - 이 스크립트를 돌리고 나면 구버전과 신버전이 같은 스키마에서 함께 동작한다.
--     구버전은 terms_agreed_at에 계속 값을 넣고, 신버전은 그 컬럼을 매핑하지 않아 NULL로 남긴다.
--   - users의 옛 컬럼 제거는 여기 넣지 않는다. 배포 전에 지우면 아직 도는 구버전 앱이
--     그 컬럼을 요구해 재기동 시 validate에서 죽고, 배포 실패 시 롤백 경로도 막힌다.
--     제거는 배포가 안정된 뒤 V4로 따로 돌린다(급하지 않다).

-- 1) 동의 이력 테이블. 항목별 최신 행이 현재 상태이고, 철회도 agreed=false인 새 행으로 남긴다.
CREATE TABLE IF NOT EXISTS user_agreements (
                                               id        BIGSERIAL PRIMARY KEY,
                                               user_id   BIGINT      NOT NULL REFERENCES users(id),
    type      VARCHAR(30) NOT NULL,                  -- TERMS_OF_SERVICE / PRIVACY_POLICY / THIRD_PARTY_SHARING / MARKETING
    agreed    BOOLEAN     NOT NULL,
    agreed_at TIMESTAMP   NOT NULL,
    version   VARCHAR(20) NOT NULL                   -- 동의 시점의 약관 버전. 기존 이관분은 'legacy'
    );

-- 유저·타입별 최신 행 조회가 유일한 접근 패턴이다.
CREATE INDEX IF NOT EXISTS idx_user_agreements_user_type
    ON user_agreements (user_id, type, agreed_at DESC);

-- 2) 기존 가입자 이관. 실제로 받은 적 없는 동의라 version='legacy'로 표시해 신규 동의와 구분한다.
--    이관해두지 않으면 기존 사용자가 전원 필수 미동의 상태가 되어 재동의 유도 화면이 필요해진다.
INSERT INTO user_agreements (user_id, type, agreed, agreed_at, version)
SELECT u.id, t.type, TRUE, u.terms_agreed_at, 'legacy'
FROM users u
         CROSS JOIN (VALUES ('TERMS_OF_SERVICE'), ('PRIVACY_POLICY'), ('THIRD_PARTY_SHARING')) AS t(type);

-- 마케팅은 기존 값을 그대로 옮긴다(대부분 false).
INSERT INTO user_agreements (user_id, type, agreed, agreed_at, version)
SELECT u.id, 'MARKETING', u.marketing_opt_in, u.terms_agreed_at, 'legacy'
FROM users u;

-- 3) 신버전 엔티티는 terms_agreed_at을 매핑하지 않으므로 INSERT에 값을 넣지 않는다.
--    NOT NULL을 풀지 않으면 신버전 배포 직후부터 V4를 돌릴 때까지 회원가입이 제약 위반으로 실패한다.
--    (marketing_opt_in은 DEFAULT FALSE가 있어 그대로 둬도 무방하다.)
ALTER TABLE users ALTER COLUMN terms_agreed_at DROP NOT NULL;