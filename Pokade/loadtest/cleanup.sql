-- #341 부하테스트 뒷정리 — 회원가입 시나리오(B)가 남긴 PENDING 유저 삭제.
-- 가입 시 user_agreements에 동의 이력이 함께 생기므로(FK) 자식부터 지운다.
--
-- 실행: docker exec -i pokade-postgres psql -U pokade -d pokade < loadtest/cleanup.sql

BEGIN;

DELETE FROM user_agreements
WHERE user_id IN (
    SELECT id FROM users
    WHERE email LIKE 'signup\_%@loadtest.local' AND status = 'PENDING'
);

DELETE FROM users
WHERE email LIKE 'signup\_%@loadtest.local' AND status = 'PENDING';

COMMIT;
