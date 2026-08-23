-- buy_offer_orders.points_used(V9)는 결제 승인 시점까지만 쓰이고, 확정된 buy_offers로는
-- 넘어가지 않아 정보가 사라졌다. 즉시판매(구매입찰 체결)로 새로 생기는 거래를 나중에 취소할 때
-- 원래 구매자가 이 입찰에 포인트를 얼마나 썼는지 알아야 PointService.refund()로 정확히
-- 돌려줄 수 있으므로, buy_offers 자체에도 남긴다.
ALTER TABLE buy_offers ADD COLUMN IF NOT EXISTS points_used INTEGER NOT NULL DEFAULT 0;
