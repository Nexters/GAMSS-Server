-- 운영 중 백오피스에서 바꾸는 LLM 설정(모델·시스템 프롬프트). 단일 행만 유지한다.
-- 행이 없으면 앱이 코드 기본값을 쓰므로 초기 시드는 넣지 않는다.
CREATE TABLE llm_settings (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    model         VARCHAR(100) NOT NULL,
    system_prompt TEXT         NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
