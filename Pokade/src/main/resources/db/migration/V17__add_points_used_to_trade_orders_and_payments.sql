-- 즉시구매도 구매입찰과 동일하게, 결제 전 포인트를 미리 사용해 결제 금액에서 차감하는 기능 추가.
-- payments.points_used는 거래 취소 시 되돌려줄 포인트 액수를 알기 위해 필요하다(전액 포인트 결제한
-- 거래는 toss_payment_key가 없어 취소 시 그쪽만으로는 환불할 금액을 알 수 없다).
ALTER TABLE trade_orders ADD COLUMN IF NOT EXISTS points_used INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS points_used INTEGER NOT NULL DEFAULT 0;
