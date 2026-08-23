-- 판매 등록 시 정산 받을 계좌와(검수 실패 등으로) 반송받을 주소를 함께 받기 위한 컬럼 추가.
-- 실제 은행 이체 연동은 이번 범위 밖(정산 자체는 계속 포인트로 처리) - 여기서는 입력값을
-- 저장만 한다. 기존 매물이 이 정보 없이 존재하므로 전부 nullable로 추가한다.
ALTER TABLE listings ADD COLUMN IF NOT EXISTS settlement_bank_name VARCHAR(50);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS settlement_account_number VARCHAR(50);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS settlement_account_holder VARCHAR(50);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS return_recipient_name VARCHAR(50);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS return_recipient_phone VARCHAR(20);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS return_address VARCHAR(255);
