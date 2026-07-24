-- 대화 종료 시 생성되는 감정 카드. 대화 1개당 카드 1개(conversation_id UNIQUE로 보장).
-- 카드가 속한 캘린더 날짜는 그 대화의 생성시간 기준이라, conversation_created_at을 비정규화해
-- 단일 테이블로 날짜·월별 조회한다(대화 시작 시각은 종료 후 바뀌지 않으므로 안전).
CREATE TABLE cards (
    id                      BIGINT      NOT NULL AUTO_INCREMENT,
    member_id               BIGINT      NOT NULL,
    conversation_id         BIGINT      NOT NULL,
    emotion                 VARCHAR(20) NOT NULL,
    summary                 TEXT        NOT NULL,
    message                 TEXT        NOT NULL,
    conversation_created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cards_conversation_id UNIQUE (conversation_id)
);

CREATE INDEX idx_cards_member_conv_created_at ON cards (member_id, conversation_created_at);
