-- 대화방 검색용 풀텍스트 인덱스.
-- 한글은 공백 기준 기본 파서로는 검색이 안 되므로 CJK용 ngram 파서(기본 bigram)를 사용한다.
-- 검색 대상: 채팅 내용(messages.content)과 대화방 제목(cards.summary).
ALTER TABLE messages ADD FULLTEXT INDEX ft_messages_content (content) WITH PARSER ngram;
ALTER TABLE cards ADD FULLTEXT INDEX ft_cards_summary (summary) WITH PARSER ngram;
