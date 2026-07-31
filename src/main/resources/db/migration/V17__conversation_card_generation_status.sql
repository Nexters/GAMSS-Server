-- 카드 생성 CAS 선점 상태. 동시에 들어온 중복 카드 생성 요청이 LLM을 여러 번 호출하지 않도록,
-- messages.comment_status와 같은 패턴으로 LLM 호출 전에 원자적으로 선점한다.
ALTER TABLE conversations ADD COLUMN card_generation_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE conversations ADD COLUMN card_generation_status_updated_at DATETIME(6) NULL;

CREATE INDEX idx_conversation_card_generation_status
    ON conversations (card_generation_status, card_generation_status_updated_at);

-- 이 마이그레이션 이전에 이미 카드가 만들어진 대화방은 상태가 기본값 NONE으로 들어간다 — 그대로 두면
-- 그 대화방으로 카드 생성 요청이 다시 들어올 때 CAS 선점에 성공해 LLM을 한 번 더 호출한 뒤에야
-- (저장 시점 유니크 제약으로) CARD_ALREADY_EXISTS를 반환하게 된다. 기존 카드가 있는 대화방은
-- DONE으로 백필해 배포 직후의 불필요한 LLM 호출을 막는다.
UPDATE conversations c
JOIN cards ON cards.conversation_id = c.id
SET c.card_generation_status = 'DONE';
