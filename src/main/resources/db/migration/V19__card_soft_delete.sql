-- 사용자가 자기 카드를 지울 수 있게 한다(단건·감정별·전체). 행을 물리 삭제하지 않고 시각만 남겨,
-- 사용자 조회에서만 감추고 백오피스 생성 이력 통계는 그대로 유지한다.
ALTER TABLE cards ADD COLUMN deleted_at DATETIME(6) NULL;

-- 캘린더·날짜별 조회가 (member_id, conversation_created_at) 으로 훑은 뒤 삭제분을 걸러내므로
-- 기존 인덱스에 deleted_at 을 덧붙여 커버링 범위를 넓힌다. deleted_at IS NULL 은 동등 매치라
-- 뒤따르는 conversation_created_at 의 범위 스캔·정렬이 그대로 살아 있다.
CREATE INDEX idx_cards_member_deleted_conv_created_at
    ON cards (member_id, deleted_at, conversation_created_at);

-- 위 인덱스가 V6 의 (member_id, conversation_created_at) 을 포함하므로 기존 인덱스는 지운다.
-- 회원 단위 카드 조회·삭제 쿼리는 모두 deleted_at IS NULL 을 걸어서, V6 인덱스로만 할 수 있는
-- 일이 남지 않는다. MySQL 8.0 에 카드 1000행(회원 50명·365일 분산)을 넣고 ANALYZE 후 실측:
--   둘 다  : 달력 조회 rows 2 (index condition) / 감정별 대상 조회 rows 18 (index condition)
--   V19 만 : 둘 다와 동일
--   V6  만 : 달력 조회 rows 2 (Using where)    / 감정별 대상 조회 rows 20 (index condition 없음)
DROP INDEX idx_cards_member_conv_created_at ON cards;
