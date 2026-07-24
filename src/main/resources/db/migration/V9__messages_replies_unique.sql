-- replies_to_message_id당 캐릭터 답글 1개만 존재하도록 DB에서 강제한다.
-- MySQL UNIQUE 인덱스는 NULL을 서로 다른 값으로 취급하므로(답장이 아닌 메시지 대부분),
-- NULL 행은 제약 대상이 아니고 실제 값이 중복될 때만 막는다.
ALTER TABLE messages
    ADD UNIQUE KEY uk_messages_replies_to_message_id (replies_to_message_id);
