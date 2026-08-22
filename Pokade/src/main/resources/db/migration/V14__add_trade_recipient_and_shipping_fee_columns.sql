-- 즉시구매도 구매입찰과 동일하게 "주문서" 단계(받는사람 정보 + 배송비 포함 최종 결제금액)를 거치도록
-- 개편하기 위한 컬럼 추가. 기존 주문/거래가 이 정보 없이 존재하므로 전부 nullable로 추가한다.
ALTER TABLE trade_orders ADD COLUMN IF NOT EXISTS shipping_fee INTEGER;
ALTER TABLE trade_orders ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(50);
ALTER TABLE trade_orders ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(20);
ALTER TABLE trade_orders ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(255);

ALTER TABLE trades ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(50);
ALTER TABLE trades ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(20);
ALTER TABLE trades ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(255);
