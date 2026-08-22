-- 구매입찰 결제 시 상품가+배송비 중 일부를 포인트로 먼저 차감하고 나머지만 토스로 결제하는
-- 기능 추가 - 실제 사용한 포인트 액수를 주문에 남겨서, 결제 승인 단계에서 그만큼만
-- PointService.use()로 차감하고 나머지 금액만 토스 승인 API에 넘길 수 있게 한다.
ALTER TABLE buy_offer_orders ADD COLUMN IF NOT EXISTS points_used INTEGER NOT NULL DEFAULT 0;
