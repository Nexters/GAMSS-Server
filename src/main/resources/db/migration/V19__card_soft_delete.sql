-- 사용자가 자기 카드를 지울 수 있게 한다(단건·감정별·전체). 행을 물리 삭제하지 않고 시각만 남겨,
-- 사용자 조회에서만 감추고 백오피스 생성 이력 통계는 그대로 유지한다.
ALTER TABLE cards ADD COLUMN deleted_at DATETIME(6) NULL;

-- 캘린더·날짜별 조회가 (member_id, conversation_created_at) 으로 훑은 뒤 삭제분을 걸러내므로
-- 기존 인덱스에 deleted_at 을 덧붙여 커버링 범위를 넓힌다.
CREATE INDEX idx_cards_member_deleted_conv_created_at
    ON cards (member_id, deleted_at, conversation_created_at);
