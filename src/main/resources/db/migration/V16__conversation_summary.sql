-- 대화방 과거 요약. 카드 생성 시점에 프론트가 보낸 대화 전체 요약을 저장해,
-- 이후 다른 대화방에서 댓글을 생성할 때 과거 맥락으로 참고한다.
ALTER TABLE conversations ADD COLUMN summary TEXT NULL;
