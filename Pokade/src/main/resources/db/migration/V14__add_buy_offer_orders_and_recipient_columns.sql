-- 구매입찰 등록 시점에 바로 토스 에스크로 결제를 진행하기 위한 주문 테이블(trade_orders/
-- point_charge_orders와 동일한 "결제 확정 전 PENDING 기록" 패턴) + 확정된 buy_offers에 받는사람
-- 정보와 결제 키를 남기기 위한 컬럼 추가.
CREATE TABLE IF NOT EXISTS buy_offer_orders (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            VARCHAR(64) NOT NULL UNIQUE,
    buyer_id            BIGINT NOT NULL REFERENCES users(id),
    card_id             BIGINT NOT NULL REFERENCES cards(id),
    variant_id          BIGINT REFERENCES card_variants(id),
    grade               VARCHAR(10),
    price               INTEGER NOT NULL,
    shipping_fee        INTEGER NOT NULL,
    recipient_name      VARCHAR(50) NOT NULL,
    recipient_phone     VARCHAR(20) NOT NULL,
    recipient_address   VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / CONFIRMED / FAILED
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE buy_offers ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(50);
ALTER TABLE buy_offers ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(20);
ALTER TABLE buy_offers ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(255);
ALTER TABLE buy_offers ADD COLUMN IF NOT EXISTS toss_payment_key VARCHAR(200);
