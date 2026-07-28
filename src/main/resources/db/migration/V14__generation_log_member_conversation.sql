-- 유저별 일일 토큰 상한(집계 대상: member_id)과 대화방별 사용량 페이지(집계 대상: conversation_id)를 위해
-- 생성 로그에 소유 회원·대화방을 붙인다. 과거 행은 이 정보가 없으므로 NULL 허용이다.
ALTER TABLE generation_log ADD COLUMN member_id       BIGINT NULL;
ALTER TABLE generation_log ADD COLUMN conversation_id BIGINT NULL;

-- 유저별 일일 사용량 합산: WHERE member_id = ? AND created_at >= (오늘 리셋시각) 조회를 받친다.
CREATE INDEX idx_generation_log_member_created_at ON generation_log (member_id, created_at);
-- 대화방별 토큰 합산: WHERE conversation_id = ? 조회를 받친다.
CREATE INDEX idx_generation_log_conversation_id ON generation_log (conversation_id);
