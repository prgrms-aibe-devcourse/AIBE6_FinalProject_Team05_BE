-- 포인트 충전(토스페이먼츠) + 매물 즉시구매 에스크로 결제에 필요한 테이블/컬럼.
-- IF NOT EXISTS류 가드는 Flyway 도입 이전 schema.sql 시절 이미 이 내용을 반영한 로컬/운영 DB가
-- 있을 수 있어 재실행에도 안전하게 남겨둔다(신규 DB에서는 그냥 정상 생성된다).

-- 토스페이먼츠 결제 승인 시 발급되는 키 - 거래 취소 시 이 키로 Toss 결제취소(환불) API를 호출한다.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS toss_payment_key VARCHAR(200);

-- 매물 즉시구매 주문 - 토스페이먼츠 결제창을 띄우기 전에 PENDING으로 먼저 기록해, 결제 승인 이후에만
-- 매물을 잠근다(TRADING 전환). point_charge_orders와 동일한 ready/confirm 패턴.
CREATE TABLE IF NOT EXISTS trade_orders (
    id           BIGSERIAL PRIMARY KEY,
    order_id     VARCHAR(64) NOT NULL UNIQUE,
    buyer_id     BIGINT NOT NULL REFERENCES users(id),
    listing_id   BIGINT NOT NULL REFERENCES listings(id),
    amount       INTEGER NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING / CONFIRMED / FAILED
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 매물 구매 시 포인트 차감 이력용 (현재는 매물 구매가 Toss 에스크로 결제로 전환되어 직접 쓰이지 않지만,
-- 컬럼 자체는 이미 배포된 스키마의 일부라 유지한다).
ALTER TABLE point_transactions ADD COLUMN IF NOT EXISTS related_trade_id BIGINT REFERENCES trades(id);

-- 포인트 충전 주문 - 토스페이먼츠 결제창을 띄우기 전에 PENDING으로 먼저 기록해, 승인 콜백에서
-- 클라이언트가 보낸 금액이 아니라 이 행의 amount를 기준으로 검증한다.
CREATE TABLE IF NOT EXISTS point_charge_orders (
    id           BIGSERIAL PRIMARY KEY,
    order_id     VARCHAR(64) NOT NULL UNIQUE,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    amount       INTEGER NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING / CONFIRMED / FAILED
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
