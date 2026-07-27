-- LLM 생성(댓글·답글) 1요청당 한 줄. 대시보드의 품질·안정성 지표(호출 수·성공률·재시도율·지연·토큰)를
-- comment_status 상태머신으로는 알 수 없는 부분까지 정확히 집계하기 위한 관측용 로그다.
CREATE TABLE generation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_type VARCHAR(20)  NOT NULL,          -- COMMENT | REPLY
    model           VARCHAR(100) NOT NULL,          -- 생성에 사용된 모델(앱 전체 단일값)
    success         BIT          NOT NULL,          -- 재시도까지 최종 성공 여부
    attempt_count   INT          NOT NULL,          -- 실제 LLM 호출 횟수(재시도 포함)
    used_tokens     INT          NULL,              -- 성공 시에만 채워짐(과금 단위)
    latency_ms      BIGINT       NOT NULL,          -- 생성 요청 전체 소요(ms)
    failure_reason  VARCHAR(255) NULL,              -- 실패 시 원인 요약(예외 분류)
    created_at      DATETIME(6)  NOT NULL
);

-- 대시보드는 항상 기간(created_at) 범위로 집계하므로 인덱스를 둔다.
CREATE INDEX idx_generation_log_created_at ON generation_log (created_at);
