ALTER TABLE messages
    ADD COLUMN root_message_id           BIGINT      NULL AFTER replies_to_message_id,
    ADD COLUMN comment_status            VARCHAR(20) NOT NULL DEFAULT 'NONE' AFTER root_message_id,
    ADD COLUMN comment_status_updated_at DATETIME(6) NULL AFTER comment_status;

CREATE INDEX idx_message_root ON messages (root_message_id);
