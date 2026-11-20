-- App Store 서버 알림으로 환불을 회수하고 놓친 구매를 지급한다(#245).

-- 처리한 알림. Apple 은 같은 알림을 다시 보낼 수 있고 알림 기록 배치도 같은 알림을 다시 가져오므로 UUID 로 거른다.
-- 정상으로 끝난(처리했거나 처리할 필요가 없던) 알림만 남긴다. 실패한 알림은 남기지 않아야 재전송과 배치가 다시 처리한다.
CREATE TABLE apple_notification (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notification_uuid VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    subtype           VARCHAR(50) NULL,
    created_at        DATETIME(6) NOT NULL,
    CONSTRAINT uk_apple_notification_uuid UNIQUE (notification_uuid)
);

-- 거래별 최신 환불 상태. 알림이 순서가 바뀌어 와도 state_signed_at 이 더 최근인 상태만 남긴다.
-- 결제 기록보다 먼저 올 수 있어(지급 전에 환불) 결제와 따로 둔다. 지급되면 이 상태를 보고 바로 회수한다.
-- source_id 는 이 상태를 만든 알림이고 원장 환불 출처로 쓴다(asn:{notificationUUID}).
CREATE TABLE apple_refund_state (
    transaction_id        VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL PRIMARY KEY,
    refunded              BIT         NOT NULL,
    -- Apple revocationPercentage(milliunits, 100000 이 100%). NULL 이면 전액 환불이다.
    revocation_percentage INT         NULL,
    source_id             VARCHAR(49) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    state_signed_at       DATETIME(6) NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL
);
