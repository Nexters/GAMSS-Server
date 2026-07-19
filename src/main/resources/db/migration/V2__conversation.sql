CREATE TABLE conversations (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_conversation_member_created (member_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE messages (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id       BIGINT       NOT NULL,
    sender_type           VARCHAR(20)  NOT NULL,
    emotion_type          VARCHAR(20)  NULL,
    content               VARCHAR(500) NOT NULL,
    replies_to_message_id BIGINT       NULL,
    created_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_message_conversation (conversation_id)
) ENGINE = InnoDB;
