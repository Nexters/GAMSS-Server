ALTER TABLE conversations
    ADD COLUMN ended_by VARCHAR(20) NULL AFTER status;
