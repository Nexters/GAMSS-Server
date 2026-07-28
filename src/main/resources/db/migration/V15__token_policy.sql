-- 유저별 일일 토큰 상한 정책. 앱 전체 단일 행(id=1)만 존재하며 백오피스에서 값을 조절한다.
-- 상한 자체의 적용 여부(prod만 적용)는 코드 설정(gamss.token-limit.enabled)이 정하고, 이 테이블은
-- '얼마나(daily_token_limit)'와 '언제 리셋(reset_hour, KST 기준 시각)'만 담는다.
CREATE TABLE token_policy (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    daily_token_limit BIGINT      NOT NULL,          -- 유저 1명이 하루에 소비할 수 있는 총 토큰(used_tokens 합)
    reset_hour        INT         NOT NULL,          -- 일일 사용량이 리셋되는 KST 시각(0~23)
    updated_at        DATETIME(6) NOT NULL
);

-- 기본값 시드: 하루 10만 토큰, 05시(KST) 리셋. 백오피스에서 조절 가능.
INSERT INTO token_policy (daily_token_limit, reset_hour, updated_at)
VALUES (100000, 5, NOW(6));
