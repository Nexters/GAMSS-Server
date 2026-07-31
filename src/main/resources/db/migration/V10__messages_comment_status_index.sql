-- comment_status 기반 조회를 위한 복합 인덱스.
-- 청소 스케줄러(resetStalePending)와 대시보드 '막힌 PENDING' 지표(countByCommentStatusOlderThan)가
-- "comment_status = 'PENDING' AND comment_status_updated_at < :threshold"로 조회하는데, 인덱스가 없어
-- messages 전체를 스캔한다(스케줄러는 1분마다). comment_status 등호 + comment_status_updated_at 범위를
-- 한 번에 타도록 복합 인덱스를 둔다 — messages는 가장 빠르게 커지는 테이블이라 스캔 비용이 누적된다.
CREATE INDEX idx_message_comment_status ON messages (comment_status, comment_status_updated_at);
