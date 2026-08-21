-- 본문·제목을 암호문으로 저장하기 위한 스키마 전환.
--
-- 컬럼을 넓히는 건 기존 값 때문이 아니라 앞으로 들어올 암호문 때문이다. 한글 500자는 UTF-8 로
-- 1,500바이트이고 여기에 IV·인증 태그가 붙어 Base64 로 감싸면 약 2,050자가 된다 — 지금 폭
-- (VARCHAR(500)) 그대로면 새 메시지 저장이 곧바로 실패한다. 제목도 같은 이유로 넓힌다.
--
-- TEXT 가 아니라 VARCHAR 로 넓히는 이유는 롤백이다. 배포 후 이전 이미지로 되돌려도 스키마는 새
-- 것이 남는데, VARCHAR -> TEXT 는 JDBC 타입이 바뀌어 옛 코드의 ddl-auto: validate 가 실패해 앱이
-- 아예 뜨지 않을 수 있다. 폭만 넓히면 그 위험이 없다(그래도 옛 코드에는 복호화가 없어 화면에는
-- 암호문이 보인다 — 롤백하려면 DB 도 함께 되돌려야 한다).
--
-- 옛 ngram 인덱스는 여기서 지운다. 암호문을 인덱싱해봐야 아무것도 못 찾고, 컬럼 타입을 바꾸려면
-- 풀텍스트 인덱스를 먼저 떼야 한다. 검색은 V32 가 만든 블라인드 인덱스로 넘어간다.

ALTER TABLE messages DROP INDEX ft_messages_content;
ALTER TABLE conversations DROP INDEX ft_conversations_title;

ALTER TABLE messages MODIFY content VARCHAR(3000) NOT NULL;
ALTER TABLE conversations MODIFY title VARCHAR(600) NULL;
