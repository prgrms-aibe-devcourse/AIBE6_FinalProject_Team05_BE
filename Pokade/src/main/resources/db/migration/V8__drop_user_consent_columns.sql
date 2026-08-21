-- V3에서 user_agreements로 이관을 마친 뒤, users에 남은 옛 동의 컬럼을 제거한다.
--
-- 실행 시점: 신버전 배포가 안정화된 "뒤" 아무 때나. 서두를 이유가 없다.
--   V3가 NOT NULL을 풀어둬서 두 컬럼은 이미 아무도 쓰지 않는 잔재이고, 신버전은 정상 동작한다.
--
-- V3와 달리 이 스크립트는 파괴적이며, 실행하는 순간 구버전으로의 롤백이 막힌다.
--   - 신버전 엔티티는 두 컬럼을 매핑하지 않고, Hibernate validate는 엔티티가 모르는
--     여분 컬럼을 문제 삼지 않으므로 신버전에는 영향이 없다.
--   - 반대로 구버전 앱은 그 컬럼을 요구하므로, 지운 뒤에는 옛 이미지로 되돌릴 수 없다.
--     배포에 문제가 없다고 확신한 다음에 실행한다.
--
-- 이관 확인은 V3를 실행한 "직후"에 아래 쿼리로 한다. 두 값이 같아야 한다.
--   SELECT (SELECT count(*) FROM user_agreements) AS 동의행,
--          (SELECT count(*) * 4 FROM users) AS 기대치;
--   배포 후에는 신규 가입자가 앱을 통해 동의를 기록하므로 이 등식으로는 이관 여부를 판별할 수 없다.
--   V3의 이관이 누락된 채 이 스크립트를 돌리면 동의 이력이 복구 불가능하게 사라진다.

-- IF EXISTS라 재실행해도 안전하다. 한 트랜잭션으로 묶어 둘 중 하나만 지워지는 상태를 막는다.
BEGIN;

ALTER TABLE users DROP COLUMN IF EXISTS terms_agreed_at;
ALTER TABLE users DROP COLUMN IF EXISTS marketing_opt_in;

COMMIT;