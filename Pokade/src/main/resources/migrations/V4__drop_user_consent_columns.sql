-- V3에서 user_agreements로 이관을 마친 뒤, users에 남은 옛 동의 컬럼을 제거한다.
--
-- 실행 시점: 새 애플리케이션 버전을 배포한 "직후". V3와 달리 이 스크립트는 파괴적이다.
--   - 신버전 엔티티는 두 컬럼을 매핑하지 않고, Hibernate validate는 엔티티가 모르는
--     여분 컬럼을 문제 삼지 않으므로 배포 후 실행해도 앱은 정상 동작한다.
--   - 반대로 배포 전에 실행하면 아직 돌고 있는 구버전 앱이 컬럼을 요구해 재기동에 실패한다.
--
-- 실행 전 확인: SELECT count(*) FROM user_agreements; 가 유저 수 x 4 인지 본다.
--   V3의 이관이 누락된 채 이 스크립트를 돌리면 동의 이력이 복구 불가능하게 사라진다.

ALTER TABLE users DROP COLUMN IF EXISTS terms_agreed_at;
ALTER TABLE users DROP COLUMN IF EXISTS marketing_opt_in;