CREATE TABLE members (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE social_accounts (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    provider    VARCHAR(30) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_provider UNIQUE (provider, provider_id)
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    member_id BIGINT       NOT NULL,
    token     VARCHAR(512) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_member UNIQUE (member_id)
) ENGINE = InnoDB;
