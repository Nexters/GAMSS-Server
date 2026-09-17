ALTER TABLE cards
    ADD COLUMN created_by VARCHAR(20) NULL AFTER conversation_id;
