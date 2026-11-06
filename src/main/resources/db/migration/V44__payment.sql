-- 인앱결제의 플랫폼 공통 기반(#242): 판매 상품, 구매 계정 바인딩, 결제 기록.
-- 스토어 검증(#243~#246)은 이 위에서 "검증"만 구현한다.

-- 판매 상품 카탈로그. 지급량은 서버가 정하고 클라이언트가 보낸 수량·금액은 믿지 않는다.
-- 가격은 스토어 SDK 가 현지 통화로 보여 주므로 두지 않는다. 바꿀 때도 마이그레이션으로 바꾼다.
CREATE TABLE token_product (
    -- 스토어 상품 ID. App Store 와 Google Play 에 같은 문자열로 등록한다.
    -- 대소문자와 뒤 공백까지 구분하도록 NO PAD 인 utf8mb4_0900_bin 으로 둔다(token_ledger.ref_id 와 같은 이유).
    product_id   VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL PRIMARY KEY,
    token_amount BIGINT       NOT NULL,
    active       BOOLEAN      NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT ck_token_product_amount CHECK (token_amount > 0)
);

INSERT INTO token_product (product_id, token_amount, active, updated_at) VALUES
    ('token_300000', 300000, TRUE, NOW(6)),
    ('token_500000', 500000, TRUE, NOW(6)),
    ('token_1000000', 1000000, TRUE, NOW(6));

-- 쿼터 주체와 스토어에 넘기는 계정 토큰(Apple appAccountToken, Google obfuscatedAccountId)의 바인딩.
-- 토큰은 무작위 UUID 다. member_id·subject_key 에서 계산하면 개인 식별자와 잇는 고리가 스토어에 남는다.
-- 웹훅으로만 들어온 구매의 주인을 이 값으로 찾으므로 탈퇴 정리 대상이 아니다.
CREATE TABLE payment_account (
    subject_key   CHAR(64)    NOT NULL PRIMARY KEY,
    account_token CHAR(36)    NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_account_token UNIQUE (account_token)
);

-- 결제 한 건당 한 행. 탈퇴 후에도 보존한다(5년). 그래서 member_id 가 아니라 subject_key 를 둔다.
CREATE TABLE payment (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_key           CHAR(64)     NOT NULL,
    platform              VARCHAR(20)  NOT NULL,
    -- Apple: transactionId, Google: SHA-256(purchaseToken) hex. 원장 멱등 키에 들어가므로 64자로 묶는다.
    -- 서버 기본 collation 은 대소문자와 뒤 공백을 무시하고 utf8mb4_bin 도 뒤 공백은 무시한다(PAD SPACE).
    -- 다른 거래가 같은 거래로 묶이지 않게 NO PAD 인 utf8mb4_0900_bin 으로 못 박는다.
    store_transaction_key VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    -- Google consume 과 재조회에 원문이 필요하다. 유니크 인덱스 길이 때문에 키와 따로 둔다.
    store_purchase_token  TEXT         NULL,
    -- Google orderId. 고객 문의 대응용이다.
    store_order_id        VARCHAR(128) NULL,
    product_id            VARCHAR(100) NOT NULL,
    quantity              INT          NOT NULL,
    -- 지급 당시 token_amount x quantity. 상품 지급량이 나중에 바뀌어도 회수량이 흔들리지 않게 남긴다.
    granted_tokens        BIGINT       NOT NULL,
    refunded_tokens       BIGINT       NOT NULL DEFAULT 0,
    -- Google 테스트 구매도 SANDBOX 다. 매출 집계에서 뺀다.
    environment           VARCHAR(20)  NOT NULL,
    purchased_at          DATETIME(6)  NOT NULL,
    -- Google consume 완료 시각. Apple 은 NULL 이다.
    consumed_at           DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    CONSTRAINT uk_payment_store_transaction UNIQUE (platform, store_transaction_key),
    CONSTRAINT ck_payment_quantity CHECK (quantity > 0),
    CONSTRAINT ck_payment_refunded CHECK (refunded_tokens BETWEEN 0 AND granted_tokens),
    -- 한 주체의 결제 내역을 최근 순으로 보는 조회(백오피스 결제 내역 #248, 고객 문의 대응)에 쓴다.
    INDEX idx_payment_subject (subject_key, created_at)
);
