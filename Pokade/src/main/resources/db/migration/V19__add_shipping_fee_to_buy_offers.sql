-- buy_offer_orders.shipping_fee(V14)와 마찬가지로, 확정된 buy_offers에도 배송비를 남겨야
-- 즉시판매(체결) 시 실제 결제된 금액(상품가+배송비-포인트사용액)을 Payment.amount에 정확히
-- 기록할 수 있다. 기존 buy_offers는 전부 고정 배송비(3000원) 하나만 써왔으므로 기본값으로 채운다.
ALTER TABLE buy_offers ADD COLUMN IF NOT EXISTS shipping_fee INTEGER NOT NULL DEFAULT 3000;
