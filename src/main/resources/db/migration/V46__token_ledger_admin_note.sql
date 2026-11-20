-- 백오피스 수동 지급·회수의 처리자와 사유(#241). 관리자 변동에만 채운다.
ALTER TABLE token_ledger
    ADD COLUMN admin_email VARCHAR(255) NULL,
    ADD COLUMN reason      VARCHAR(500) NULL;
