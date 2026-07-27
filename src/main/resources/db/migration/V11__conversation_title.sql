-- 대화방 제목. 첫 생성 시에는 없고(null), 이후 클라이언트가 지정한다(여러 번 수정 가능).
ALTER TABLE conversations ADD COLUMN title VARCHAR(100) NULL;
