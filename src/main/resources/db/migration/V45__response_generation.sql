-- 응답을 만든 프롬프트 버전을 남기기 위해 현재 버전을 설정 행에 둔다(#255).
ALTER TABLE llm_settings ADD COLUMN prompt_version INT NULL;

UPDATE llm_settings s
SET s.prompt_version = (SELECT MAX(r.version) FROM prompt_revisions r WHERE r.prompt_type = s.prompt_type);

-- 생성 1회의 기록. trigger_message_id 는 생성을 일으킨 유저 메시지(일기 또는 답장)다.
CREATE TABLE response_generation (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trigger_message_id    BIGINT       NOT NULL,
    conversation_id       BIGINT       NOT NULL,
    kind                  VARCHAR(20)  NOT NULL,
    model                 VARCHAR(100) NULL,
    common_prompt_version INT          NULL,
    type_prompt_version   INT          NULL,
    created_at            DATETIME(6)  NOT NULL,
    CONSTRAINT uk_response_generation_trigger UNIQUE (trigger_message_id)
);

CREATE INDEX idx_response_generation_created ON response_generation (created_at);
CREATE INDEX idx_response_generation_conversation ON response_generation (conversation_id);
