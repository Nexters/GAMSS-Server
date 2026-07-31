-- 대화방 검색용 풀텍스트 인덱스.
-- 한글은 공백 기준 기본 파서로는 검색이 안 되므로 CJK용 ngram 파서(기본 bigram)를 사용한다.
-- 검색 대상: 채팅 내용(messages.content)과 클라이언트가 지정한 대화방 제목(conversations.title).
-- 카드 요약(cards.summary)은 카드에 표시할 문구이지 대화방 제목이 아니라 검색 대상이 아니다.
ALTER TABLE messages ADD FULLTEXT INDEX ft_messages_content (content) WITH PARSER ngram;
ALTER TABLE conversations ADD FULLTEXT INDEX ft_conversations_title (title) WITH PARSER ngram;
