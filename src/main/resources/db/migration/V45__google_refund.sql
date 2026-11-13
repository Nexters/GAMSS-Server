-- Google Play 환불 회수와 놓친 구매 지급(#246).

-- RTDN(Pub/Sub push) 처리 기록. Pub/Sub 는 같은 메시지를 다시 보낼 수 있어 messageId 로 거른다.
-- 정상으로 끝난(처리했거나 처리할 필요가 없던) 메시지만 남긴다. 실패한 메시지는 남기지 않아야 재전송을 처리한다.
CREATE TABLE google_notification (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    -- ONE_TIME_PRODUCT, VOIDED_PURCHASE, PENDING_REFUND_REVIEW, TEST, OTHER
    notification_kind VARCHAR(30)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uk_google_notification_message UNIQUE (message_id)
);

-- 대사 배치가 처리한 Voided Purchases 항목. 같은 구간을 다시 조회해도 한 번만 처리한다.
-- source_id 는 원장 환불 출처와 같은 값이다(voided:{voidedTimeMillis}).
CREATE TABLE google_voided_purchase (
    id                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_transaction_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    source_id             VARCHAR(49) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    -- Voided Purchases API 값 그대로다. voided_source 0 사용자, 1 개발자, 2 Google.
    -- voided_reason 5 사기, 6 친구 사기, 7 지불 거절, 8 미승인 구매 등. 반복 지불 거절 같은 남용을 나중에 찾으려고 남긴다.
    voided_source         TINYINT     NULL,
    voided_reason         TINYINT     NULL,
    created_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_google_voided_purchase UNIQUE (store_transaction_key, source_id)
);

-- 주기 작업의 마지막 성공 시각. 실패하면 갱신하지 않아 다음 실행이 빠진 구간부터 다시 본다.
CREATE TABLE payment_job_checkpoint (
    job_name          VARCHAR(50) NOT NULL PRIMARY KEY,
    last_succeeded_at DATETIME(6) NOT NULL
);
