-- 백오피스 접근 허용 관리자(이메일). ADMIN_EMAILS 환경 부트스트랩과 합쳐 허용 판정에 쓰인다.
-- 이메일은 정규화(공백 제거·소문자)해 저장하고 중복은 UNIQUE로 막는다. created_by_email은 추가한 관리자.
CREATE TABLE admin_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    created_by_email VARCHAR(255),
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_accounts_email UNIQUE (email)
);
