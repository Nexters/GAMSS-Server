-- 암호화된 본문·제목을 대신 검색할 블라인드 인덱스 컬럼.
--
-- 원문이 암호문이 되면 MATCH(content) AGAINST(...) 가 죽는다. 그래서 평문을 bigram 으로 쪼개
-- HMAC 앞 8바이트를 hex 로 만든 뒤 원문 순서대로 공백으로 이어 붙인 토큰열을 따로 저장하고,
-- 검색은 이 컬럼에 건다(com.nexters.gamss.global.crypto.BlindIndexer).
--
-- ngram 파서가 아니라 기본 파서를 쓴다 — 토큰이 이미 공백으로 구분된 hex 라 공백 기준으로
-- 자르는 것이 맞다. 검색어도 같은 토큰열로 바꿔 구문 검색("t1 t2")하면 토큰이 그 순서로 인접한
-- 행만 걸려, ngram 이 하던 부분 일치가 그대로 재현된다. 토큰은 16자라 innodb_ft_min_token_size
-- (기본 3)에 걸리지 않는다.
--
-- 별도 토큰 테이블 대신 같은 행의 컬럼으로 둔 이유는 (1) 순서가 보존돼 오탐이 없고 (2) 메시지
-- 하나가 수백 행으로 불어나지 않으며 (3) 메시지가 지워질 때 인덱스도 함께 사라져 따로 정리할
-- 것이 없기 때문이다.
--
-- 기존 행은 이 컬럼이 NULL 이라 검색되지 않는다. 백필하지 않고 V34 에서 정리한다.

ALTER TABLE messages ADD COLUMN content_index TEXT NULL;
ALTER TABLE conversations ADD COLUMN title_index TEXT NULL;

ALTER TABLE messages ADD FULLTEXT INDEX ft_messages_content_index (content_index);
ALTER TABLE conversations ADD FULLTEXT INDEX ft_conversations_title_index (title_index);
