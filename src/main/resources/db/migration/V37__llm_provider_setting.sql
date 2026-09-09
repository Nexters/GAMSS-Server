-- 지금 쓰는 Gemini 호출 경로(AI Studio / Vertex AI). 앱 전체 단일 행이며 백오피스에서 전환한다.
-- 인증 값 자체는 코드 설정(gemini.ai-studio.*, gemini.vertex.*)이 갖고, 이 테이블은 '어느 쪽을 쓰는가'만 담는다.
-- 두 번째 행이 생기면 읽는 행과 쓰는 행이 갈려 전환이 조용히 무시되므로, 단일 행을 DB가 강제한다.
-- AUTO_INCREMENT 는 쓰지 않는다 - MySQL 은 auto increment 컬럼을 CHECK 에서 참조하지 못한다.
CREATE TABLE llm_provider_setting (
    id         BIGINT      NOT NULL PRIMARY KEY,
    provider   VARCHAR(20) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_llm_provider_setting_single_row CHECK (id = 1)
);

-- 지금까지 쓰던 경로를 그대로 이어받는다. 전환은 백오피스에서 한다.
INSERT INTO llm_provider_setting (id, provider, updated_at)
VALUES (1, 'AI_STUDIO', NOW(6));
