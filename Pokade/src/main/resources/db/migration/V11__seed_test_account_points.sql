-- 테스트 계정 포인트 지급 (dev/staging 전용 시드 데이터)
-- prod에 해당 이메일이 없으면 0행 업데이트로 안전하게 no-op 처리됨
UPDATE users
SET point_balance = 999999
WHERE email IN ('test1@pokade.com', 'test2@pokade.com');
