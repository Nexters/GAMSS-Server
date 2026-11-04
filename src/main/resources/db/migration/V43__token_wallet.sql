-- 구매한 유료 토큰의 잔액과 변동 원장(#238).
-- 쿼터 주체에 귀속되므로 탈퇴 정리 대상이 아니다. 결제 기록과 함께 보존한다.
CREATE TABLE token_wallet (
    subject_key CHAR(64)    NOT NULL PRIMARY KEY,
    -- 환불 회수로 음수가 될 수 있다.
    balance     BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME(6) NOT NULL
);

CREATE TABLE token_ledger (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_key   CHAR(64)     NOT NULL,
    type          VARCHAR(32)  NOT NULL,
    amount        BIGINT       NOT NULL,
    balance_after BIGINT       NOT NULL,
    -- 서버 기본 collation 은 대소문자와 뒤 공백을 무시하고, utf8mb4_bin 도 뒤 공백은 무시한다(PAD SPACE).
    -- 다른 키가 같은 키로 묶이지 않게 NO PAD 인 utf8mb4_0900_bin 으로 못 박는다.
    ref_id        VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_token_ledger_ref_id UNIQUE (ref_id),
    -- 같은 주체의 기록은 지갑 행 잠금 아래에서 쌓이므로 id 순서가 곧 반영 순서다.
    INDEX idx_token_ledger_subject (subject_key, id)
);
