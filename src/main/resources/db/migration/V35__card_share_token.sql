-- 카드 공유 링크(gamss.kr/c/{share_token})가 가리키는 토큰.
--
-- 카드 id 를 URL 에 쓸 수 없어서 따로 둔다. id 는 AUTO_INCREMENT 라 /c/1, /c/2 로 남의 카드를
-- 순서대로 긁을 수 있고, 카드의 summary·message 는 컬럼 단위로 암호화한 값이다(V33). 순번을
-- 공개 URL 에 노출하면 그 암호화가 무의미해진다.
--
-- NULL 을 허용한다 — **공유 버튼을 누른 카드에만 발급**한다. 모든 카드에 미리 만들면 아직 아무에게도
-- 주지 않은 링크가 DB 에 쌓이고, 그중 하나만 새어도 바로 열린다. MySQL 의 UNIQUE 는 NULL 을 서로
-- 다른 값으로 보므로 미발급 행이 몇이든 제약에 걸리지 않는다.
--
-- collation 을 컬럼에 못 박는다(utf8mb4_bin). 서버 기본값은 대소문자를 무시하는 collation 이라
-- 대소문자만 다른 두 토큰이 **같은 유니크 키**가 되고, 조회가 남의 카드를 맞다고 돌려줄 수 있다.
-- 토큰은 대소문자를 구분하는 불투명한 값이라 언어 인식 비교를 적용할 이유 자체가 없다.
-- (device_tokens.token 도 같은 이유로 utf8mb4_bin 이다 - V31.)
--
-- 길이 22 는 랜덤 16바이트를 패딩 없는 base64url 로 적은 결과다(128비트). 추측으로 맞히는 것은
-- 불가능하고, 충돌도 사실상 일어나지 않아 재시도 경로를 두지 않는다.
ALTER TABLE cards
    ADD COLUMN share_token VARCHAR(22) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    ADD CONSTRAINT uk_cards_share_token UNIQUE (share_token);
