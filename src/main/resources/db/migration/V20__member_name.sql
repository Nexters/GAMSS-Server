-- 소셜 로그인 시 제공자에게 받은 사용자 이름. 제공자가 주지 않으면 NULL.
ALTER TABLE members
    ADD COLUMN name VARCHAR(255) NULL AFTER email;
