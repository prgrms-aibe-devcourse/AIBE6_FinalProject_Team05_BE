-- 회원 약관 동의 이력 테이블 도입. 지금까지는 users.terms_agreed_at / marketing_opt_in에
-- 값이 하드코딩되어 실제 동의를 받은 적이 없다(가입 요청에 동의 필드 자체가 없었다).
--
-- V2와 같은 전제로 작성한다 — 운영은 ddl-auto=validate, sql.init.mode=never이므로
-- 배포 담당자가 psql로 수동 1회 실행한다.
--
-- 실행 시점: 새 애플리케이션 버전을 배포하기 "직전".
--   - 이 스크립트는 테이블 생성과 데이터 이관뿐이라 전부 "추가"다. 구버전 앱은 이 테이블을
--     모르지만 존재 자체가 무해하므로 먼저 실행해도 계속 동작한다.
--   - users의 옛 컬럼 제거는 여기 넣지 않는다. 구버전 앱이 그 컬럼을 요구하므로 배포 전에
--     지우면 재기동 시 validate에서 죽는다. 제거는 배포 "직후" V4로 분리했다.

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