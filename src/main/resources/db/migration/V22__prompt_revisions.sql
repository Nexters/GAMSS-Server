-- 프롬프트 편집 이력(append-only). 누가·언제·무엇을 저장했는지 남기고 특정 버전 복원의 근거가 된다.
-- 행은 수정·삭제하지 않으며, 복원도 새 리비전으로 쌓여 이력이 끊기지 않는다.
CREATE TABLE prompt_revisions (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    prompt_type           VARCHAR(20)  NOT NULL,
    version               INT          NOT NULL,
    system_prompt         TEXT         NOT NULL,
    saved_by              VARCHAR(255) NULL,
    restored_from_version INT          NULL,
    created_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_prompt_revisions_type_version UNIQUE (prompt_type, version)
) ENGINE = InnoDB;

-- 이미 운영 중인 프롬프트(llm_settings)를 각 타입의 v1로 시딩한다 — 이력의 시작점을 현재 상태로
-- 맞춰 도입 직후부터 '무엇에서 무엇으로 바뀌었는지' 추적할 수 있게 한다. saved_by NULL은 시스템 기록.
INSERT INTO prompt_revisions (prompt_type, version, system_prompt, saved_by, restored_from_version, created_at)
SELECT prompt_type, 1, system_prompt, NULL, NULL, updated_at
FROM llm_settings;
