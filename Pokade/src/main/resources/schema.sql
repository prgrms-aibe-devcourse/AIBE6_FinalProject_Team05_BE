-- =========================================================
-- 팀 ERD 기준 전체 스키마 (20개 테이블)
-- 생성 순서: FK 의존성 고려 (참조 대상 테이블을 먼저 생성)
-- =========================================================

-- ---------- 1. Scrydex 연동 카드 도메인 ----------

CREATE TABLE IF NOT EXISTS expansions (
    id             VARCHAR(50) PRIMARY KEY,                 -- Scrydex 세트 ID(예: base1), AUTO_INCREMENT 아님
    name           VARCHAR(100) NOT NULL,                   -- 예: 'Base'
    series         VARCHAR(100),
    code           VARCHAR(20),                              -- 세트 고유 코드(예: 'BLK')
    total          INTEGER,                                  -- 시크릿 레어 포함 세트 전체 카드 수
    language_code  VARCHAR(10),                              -- EN / JA
    release_date   DATE,                                     -- Scrydex 응답은 'YYYY/MM/DD' 슬래시 포맷 - 변환 필요
    logo           VARCHAR(255),                             -- 세트 로고 URL
    symbol         VARCHAR(255),                             -- 세트 심볼 URL
    translation    JSONB,                                    -- 영어 외 언어 세트의 en.name 등 영문 대응값
    synced_at      TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS cards (
    id                        BIGSERIAL PRIMARY KEY,
    external_id               VARCHAR(50) UNIQUE,             -- Pokemon TCG API 원본 ID → Scrydex ID(예: base1-4)로 재사용
    name                      VARCHAR(200) NOT NULL,
    set_name                  VARCHAR(100),                   -- 비정규화 - expansions.name과 중복이지만 조인 없이 표시할 때 사용
    type                      VARCHAR(50),                     -- ★Scrydex연동 - 폐기 예정, types(배열)로 대체 예정
    rarity                    VARCHAR(100),
    image_url                 VARCHAR(255),                   -- ★Scrydex연동 - image_medium과 중복, 신규 컬럼 사용 권장
    supertype                 VARCHAR(50),                     -- Pokémon / Trainer / Energy
    subtypes                  TEXT[],                          -- 예: {'Stage 2'}
    types                     TEXT[],                          -- 예: {'Fire'} - 기존 type 대체
    evolves_from              TEXT[],                          -- 이 카드가 어떤 포켓몬에서 진화했는지
    printed_number            VARCHAR(50),                     -- 카드에 실제 인쇄된 번호(예: 4/102)
    rarity_code               VARCHAR(50),                     -- 레어도 코드(예: ★H) - Trainer/Galarian Gallery류 특수판은 20자를 넘는 값도 있어 50으로 확장
    hp                        VARCHAR(10),                     -- ★Scrydex연동 - 공식 API 필드 목록 외 추가 컬럼
    artist                    VARCHAR(200),
    national_pokedex_numbers  INTEGER[],
    image_small               VARCHAR(255),
    image_medium              VARCHAR(255),
    image_large               VARCHAR(255),
    expansion_id              VARCHAR(50) REFERENCES expansions(id),
    expansion_sort_order      INTEGER,
    language_code             VARCHAR(10),                    -- EN / JA
    view_count                INTEGER NOT NULL DEFAULT 0,     -- 인기순(조회수) 정렬용
    synced_at                 TIMESTAMP
);

-- cards.hp: 이미 cards 테이블이 존재하는 로컬 DB는 CREATE TABLE IF NOT EXISTS가 스킵되어
-- 위 CREATE TABLE 본문의 컬럼 추가가 반영되지 않으므로, 기존 DB에도 적용되도록 별도 ALTER 필요.
ALTER TABLE cards ADD COLUMN IF NOT EXISTS hp VARCHAR(10);

-- cards.rarity_code: 기존 VARCHAR(20)에는 Trainer/Galarian Gallery류 특수판의 긴 코드값이 안 들어가
-- 전체 동기화 중 80건이 실패했음 - 이미 컬럼이 존재하는 DB에도 반영되도록 별도 ALTER 필요.
ALTER TABLE cards ALTER COLUMN rarity_code TYPE VARCHAR(50);

CREATE TABLE IF NOT EXISTS card_variants (
    id            BIGSERIAL PRIMARY KEY,
    card_id       BIGINT NOT NULL REFERENCES cards(id),
    variant_name  VARCHAR(100) NOT NULL,                      -- 예: unlimitedHolofoil, firstEditionShadowlessHolofoil
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,             -- 화면 대표 변형(카드당 정확히 1개), 아래 부분 유니크 인덱스로 강제
    image_small   VARCHAR(255),
    image_large   VARCHAR(255),
    synced_at     TIMESTAMP NOT NULL,
    UNIQUE (card_id, variant_name)
);

-- 대표 변형은 카드당 정확히 1개만 허용 (dbdiagram.io는 부분 인덱스 미지원이라 그림엔 표현 불가했던 제약)
CREATE UNIQUE INDEX IF NOT EXISTS uk_variants_one_primary ON card_variants(card_id) WHERE is_primary = TRUE;

CREATE TABLE IF NOT EXISTS card_prices (
    id                BIGSERIAL PRIMARY KEY,
    variant_id        BIGINT NOT NULL REFERENCES card_variants(id),
    price_type        VARCHAR(10) NOT NULL,                   -- 'raw' | 'graded'
    grade             VARCHAR(10) NOT NULL DEFAULT '',        -- graded 전용('10','9.5'), raw면 빈 문자열
    company           VARCHAR(20) NOT NULL DEFAULT '',        -- graded 전용(PSA/CGC/BGS/TAG/SGC), raw면 빈 문자열
    low               NUMERIC(14,2),
    mid               NUMERIC(14,2),                          -- graded 전용
    high              NUMERIC(14,2),                          -- graded 전용
    market            NUMERIC(14,2),                          -- 메인 표시값
    currency          VARCHAR(10),                            -- USD | JPY - 원화 환산 방식 팀 확정 필요
    change_1d_pct     NUMERIC(8,2),
    change_7d_pct     NUMERIC(8,2),                            -- 급등락 랭킹 산출에 사용
    change_14d_pct    NUMERIC(8,2),
    change_30d_pct    NUMERIC(8,2),
    change_90d_pct    NUMERIC(8,2),
    change_180d_pct   NUMERIC(8,2),
    change_7d_amount  NUMERIC(14,2),
    updated_at        TIMESTAMP NOT NULL,
    UNIQUE (variant_id, price_type, grade, company)
);

CREATE TABLE IF NOT EXISTS pokedex_ko_names (
    pokedex_number  INT PRIMARY KEY,
    name_en         VARCHAR(50) NOT NULL,
    name_ko         VARCHAR(50) NOT NULL,
    name_ko_chosung VARCHAR(30)
);
-- 한글 카드 검색용 도감번호-한글명 매핑 (PokeAPI 원본 데이터, 정적 고정값)
-- name_en: 카드 이름에서 종 이름 부분을 찾아 한글로 치환할 때 기준이 되는 영문 종명
-- name_ko_chosung: 초성 검색용, 서버 기동 시 KoreanTextUtil.extractChosung()으로 계산해서 채움

CREATE TABLE IF NOT EXISTS price_snapshots (
    id             BIGSERIAL PRIMARY KEY,
    variant_id     BIGINT NOT NULL REFERENCES card_variants(id),
    price_type     VARCHAR(10) NOT NULL,
    grade          VARCHAR(10) NOT NULL DEFAULT '',
    company        VARCHAR(20) NOT NULL DEFAULT '',
    market         NUMERIC(14,2),
    low            NUMERIC(14,2),
    currency       VARCHAR(10),
    snapshot_date  DATE NOT NULL,                              -- 차트용 - API 호출 없이 card_prices에서 일 1회 복사
    UNIQUE (variant_id, price_type, grade, company, snapshot_date)
);

CREATE TABLE IF NOT EXISTS sync_logs (
    id              BIGSERIAL PRIMARY KEY,
    sync_type       VARCHAR(20) NOT NULL,                     -- EXPANSION / CARD / PRICE / SNAPSHOT
    target          VARCHAR(100),                              -- 세트 ID 등
    status          VARCHAR(20) NOT NULL,                      -- SUCCESS / PARTIAL / FAILED
    records_synced  INTEGER,
    credits_used    INTEGER,                                   -- Scrydex 크레딧 소모량 - 예산 추적용
    error_message   TEXT,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP
);

-- ---------- 2. 회원 ----------

CREATE TABLE IF NOT EXISTS users (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) UNIQUE NOT NULL,
    password              VARCHAR(255),                        -- 소셜 로그인 시 null 가능
    nickname              VARCHAR(50) UNIQUE NOT NULL,
    nickname_changed_at   TIMESTAMP,
    provider              VARCHAR(20) NOT NULL,                -- LOCAL / GOOGLE / KAKAO
    role                  VARCHAR(20) NOT NULL,                -- USER / ADMIN
    status                VARCHAR(20) NOT NULL,                -- PENDING / ACTIVE / SUSPENDED / WITHDRAWAL_PENDING / DELETED
    profile_image_url     VARCHAR(255),
    birth_date            DATE,
    phone_number          VARCHAR(20),
    terms_agreed_at       TIMESTAMP NOT NULL,                  -- 필수약관 동의 시각
    marketing_opt_in      BOOLEAN NOT NULL DEFAULT FALSE,      -- 선택 동의(마케팅 수신)
    point_balance         INTEGER NOT NULL DEFAULT 0,
    deleted_at            TIMESTAMP,
    withdrawal_requested_at TIMESTAMP,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT NOT NULL DEFAULT 0
);
-- 참고: email_verifications는 DB 테이블로 만들지 않음 - Redis TTL로만 관리(정책 확정)

-- ---------- 3. 거래 ----------

CREATE TABLE IF NOT EXISTS listings (
    id                  BIGSERIAL PRIMARY KEY,
    card_id             BIGINT NOT NULL REFERENCES cards(id),
    seller_id           BIGINT NOT NULL REFERENCES users(id),
    variant_id          BIGINT REFERENCES card_variants(id),   -- NULL이면 대표 변형(is_primary) 기준으로 시세 계산
    price               INTEGER NOT NULL,
    grade               VARCHAR(10),                            -- S / A / B / null
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/TRADING/SOLD/EXPIRED/CANCELLED/HIDDEN
    stale_notice_sent   BOOLEAN NOT NULL DEFAULT FALSE,         -- 30일차 1차 알림 발송 여부
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS buy_offers (
    id                  BIGSERIAL PRIMARY KEY,
    card_id             BIGINT NOT NULL REFERENCES cards(id),
    buyer_id            BIGINT NOT NULL REFERENCES users(id),
    variant_id          BIGINT REFERENCES card_variants(id),
    price               INTEGER NOT NULL,
    grade               VARCHAR(10),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/MATCHED/PARTIAL/EXPIRED/CANCELLED
    expires_at          TIMESTAMP,                              -- 입찰 유효기간
    price_updated_at    TIMESTAMP,                              -- 매칭 시간우선순위 기준(가격 정정 시 갱신)
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_buy_offers_orderbook
    ON buy_offers(card_id, variant_id, status, price DESC);

CREATE TABLE IF NOT EXISTS trades (
    id             BIGSERIAL PRIMARY KEY,
    listing_id     BIGINT NOT NULL REFERENCES listings(id),
    buyer_id       BIGINT NOT NULL REFERENCES users(id),
    price          INTEGER NOT NULL,
    status         VARCHAR(20) NOT NULL,                        -- PENDING/SHIPPED_TO_PLATFORM/INSPECTED/DELIVERED/COMPLETED/CANCELLED
    shipped_at     TIMESTAMP,
    inspected_at   TIMESTAMP,
    delivered_at   TIMESTAMP,
    confirmed_at   TIMESTAMP,
    settled_at     TIMESTAMP,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 카드별 체결 내역 조회(FR-PRICE-02, listings.card_id 조인) 최적화
CREATE INDEX IF NOT EXISTS idx_listings_card_id ON listings(card_id);

-- 매도 호가창(orderbook) 조회 최적화 (카드/변형 기준 ACTIVE 매물 가격 오름차순)
CREATE INDEX IF NOT EXISTS idx_listings_orderbook
    ON listings(card_id, variant_id, status, price ASC);
CREATE INDEX IF NOT EXISTS idx_trades_listing_status ON trades(listing_id, status, confirmed_at DESC);

CREATE TABLE IF NOT EXISTS payments (
    id            BIGSERIAL PRIMARY KEY,
    trade_id      BIGINT NOT NULL UNIQUE REFERENCES trades(id),
    buyer_id      BIGINT NOT NULL REFERENCES users(id),
    amount        INTEGER NOT NULL,
    method        VARCHAR(20) NOT NULL,                        -- CARD / EASY_PAY 등
    status        VARCHAR(20) NOT NULL,                        -- PAID/ESCROW_HELD/SETTLED/REFUNDED
    paid_at       TIMESTAMP,
    refunded_at   TIMESTAMP,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 4. 포트폴리오 / AI 진단 / 포인트 ----------

CREATE TABLE IF NOT EXISTS portfolio_items (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    card_id          BIGINT NOT NULL REFERENCES cards(id),
    variant_id       BIGINT REFERENCES card_variants(id),      -- NULL이면 대표 변형 기준으로 평가액 계산
    quantity         INTEGER NOT NULL DEFAULT 1,                -- 동일 카드 복수 보유 지원
    acquired_price   INTEGER,
    acquired_at      TIMESTAMP,
    trade_id         BIGINT UNIQUE REFERENCES trades(id)
);

CREATE TABLE IF NOT EXISTS grade_results (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    card_id             BIGINT REFERENCES cards(id),            -- Vision 식별 후 매핑; 미식별 시 NULL 허용
    variant_id          BIGINT REFERENCES card_variants(id),   -- Vision이 식별한 변형(선택)
    status              VARCHAR(20) NOT NULL DEFAULT 'SUCCESS', -- SUCCESS / QUALITY_FAIL
    grade               VARCHAR(10),                            -- S / A / B (QUALITY_FAIL 시 NULL)
    centering_score     NUMERIC(6,2),
    edge_score          NUMERIC(6,2),
    surface_score       NUMERIC(6,2),
    corner_score        NUMERIC(6,2),
    is_free             BOOLEAN NOT NULL DEFAULT FALSE,
    point_used          INTEGER NOT NULL DEFAULT 0,
    confidence          NUMERIC(5,2),                           -- AI 카드 자동식별 신뢰도(0~100), 정책 80% 기준 분기에 사용
    vision_card_id      VARCHAR(50),                            -- Vision이 식별한 카드의 Scrydex ID, 신뢰도 80% 미만이면 NULL 가능
    vision_confidence   NUMERIC(5,2),                           -- Vision 응답 원본 신뢰도값, 추후 임계값 조정 근거
    retry_allowed       BOOLEAN NOT NULL DEFAULT FALSE,         -- QUALITY_FAIL 시 무료 재업로드 1회 허용 여부
    retry_used          BOOLEAN NOT NULL DEFAULT FALSE,         -- 무료 재업로드 사용 여부
    retry_of_id         BIGINT REFERENCES grade_results(id),   -- 재업로드인 경우 원본 요청 ID
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS grade_result_images (
    id                 BIGSERIAL PRIMARY KEY,
    grade_result_id    BIGINT NOT NULL REFERENCES grade_results(id),
    photo_type         VARCHAR(20),                             -- FRONT/BACK/CORNER_TL/CORNER_TR/CORNER_BL/CORNER_BR
    image_url          VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS point_transactions (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT NOT NULL REFERENCES users(id),
    type                      VARCHAR(20) NOT NULL,             -- CHARGE / USE / REFUND
    amount                    INTEGER NOT NULL,
    balance_after             INTEGER NOT NULL,
    related_grade_result_id   BIGINT REFERENCES grade_results(id),
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 5. 워치리스트 / 알림 / 챗봇 ----------

CREATE TABLE IF NOT EXISTS watchlist (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    card_id             BIGINT NOT NULL REFERENCES cards(id),
    variant_id          BIGINT REFERENCES card_variants(id),   -- 어느 변형의 목표가인지, NULL이면 대표 변형 기준
    target_buy_price    INTEGER,
    target_sell_price   INTEGER,
    is_notified         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, card_id)
);
-- 참고: 워치리스트 유저당 20개 제한은 애플리케이션 레벨에서 검증(DB 제약 아님)

CREATE TABLE IF NOT EXISTS notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    type         VARCHAR(30) NOT NULL,                          -- PRICE_TARGET / TRADE_CONFIRMED / LISTING_STALE 등
    message      VARCHAR(255),
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    session_id   VARCHAR(100) NOT NULL,
    user_id      BIGINT REFERENCES users(id),
    role         VARCHAR(10) NOT NULL,                          -- USER / ASSISTANT
    content      TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------- 6. 신고 / 제재 ----------

CREATE TABLE IF NOT EXISTS reports (
    id            BIGSERIAL PRIMARY KEY,
    target_type   VARCHAR(20) NOT NULL,                         -- LISTING / USER / TRADE
    target_id     BIGINT NOT NULL,
    reporter_id   BIGINT NOT NULL REFERENCES users(id),
    reason        VARCHAR(255),
    status        VARCHAR(20) NOT NULL,                         -- PENDING/REVIEWED/ACCEPTED/DISMISSED
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reporter_id, target_type, target_id)
    -- 🔴 DISMISSED된 신고도 이 유니크 제약상 영구 재신고 불가 - 팀 확인 필요
);

CREATE TABLE IF NOT EXISTS user_sanctions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    type         VARCHAR(30),                                    -- WARNING / LISTING_BAN / ACCOUNT_SUSPEND
    reason       VARCHAR(255),
    starts_at    TIMESTAMP NOT NULL,
    ends_at      TIMESTAMP,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 참고: Refresh Token은 Redis 기반 블랙리스트 관리로, 관계형 DB 테이블로 별도 생성하지 않음
-- 참고: 매물 5분 임시잠금은 Redis(TTL)로 처리 권장, DB 컬럼 추가 안 함

-- ---------- 7. 1:1 문의 ----------

CREATE TABLE IF NOT EXISTS inquiries (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    title        VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
