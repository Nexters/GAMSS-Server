ALTER TABLE llm_settings
    ADD COLUMN prompt_type VARCHAR(20) default 'COMMENT' not null,
    ADD UNIQUE KEY uk_llm_settings_prompt_type (prompt_type);
