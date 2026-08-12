-- Trade 배송 흐름(SHIPPED_TO_PLATFORM/INSPECTED/DELIVERED) 도입에 따른 운영 DB 마이그레이션.
--
-- 운영 환경은 spring.jpa.hibernate.ddl-auto=validate, spring.sql.init.mode=never라
-- 애플리케이션이 스키마/데이터를 자동으로 바꾸지 않는다. 이 스크립트는 배포 담당자가
-- psql 등으로 운영 DB에 수동으로 1회 실행해야 한다.
--
-- 실행 시점: 반드시 새 애플리케이션 버전을 배포하기 "직전"에 실행한다.
--   - ddl-auto=validate는 앱 기동 시 엔티티가 요구하는 컬럼이 이미 DB에 있어야 하므로,
--     컬럼 추가(1번)가 먼저 끝나 있지 않으면 새 버전이 기동 자체를 못 한다.
--   - 반대로 이 스크립트를 먼저 실행해도 구버전 앱은 문제없이 계속 동작한다 — Hibernate는
--     엔티티가 매핑하지 않는 "여분의 컬럼"이 있어도 검증에서 걸지 않고, 구버전 TradeStatus
--     enum이 모르는 값으로 상태를 바꾸는 코드도 없기 때문(2번 참고).
--
-- 현재 배포 방식(.github/workflows/deploy.yml)은 컨테이너를 정지 후 재기동하는 방식이라
-- 신/구 버전이 동시에 트래픽을 받는 진짜 "롤링" 배포는 아니지만, 나중에 다중 인스턴스로
-- 바뀌더라도 안전하도록 아래 값 매핑은 신/구 버전 enum이 공유하는 값(PENDING)만 사용한다.

-- 1) 신규 컬럼 추가 — 기존(구버전) 앱이 모르는 컬럼이라도 존재 자체는 무해하므로 먼저 추가.
ALTER TABLE trades ADD COLUMN IF NOT EXISTS inspected_at TIMESTAMP;
ALTER TABLE trades ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;

-- 2) 더 이상 존재하지 않는 상태값(MATCHED, CONFIRMED) 마이그레이션.
--    두 값 다 실제 코드에서 한 번도 저장된 적이 없는 죽은 상태였지만(예방적 조치),
--    혹시 남아있다면 신/구 버전 enum이 공통으로 아는 PENDING으로 되돌린다.
--    COMPLETED로 매핑하지 않는 이유: 실제로 완료되지 않은 거래를 완료로 오인시킬 위험이 있음.
UPDATE trades SET status = 'PENDING' WHERE status IN ('MATCHED', 'CONFIRMED');
