-- 엉뚱이 소재 목록을 코드에서 DB로 이관한다(한 줄에 소재 하나). 프롬프트와 같은 저장소를 쓰므로
-- 백오피스 편집·버전 이력·복원이 그대로 적용된다. 이미 값이 있으면 건드리지 않는다.
-- model은 COMMON 행만 읽히는 자리표시자라 상수 대신 그 시점의 COMMON 값을 참조한다(V23 주석 참고).
-- COMMON 행은 V23이 보장하므로 서브쿼리는 NULL이 될 수 없다.
INSERT INTO llm_settings (prompt_type, model, system_prompt, updated_at)
SELECT 'EONGTTUNG_TOPIC', (SELECT s2.model FROM (SELECT model FROM llm_settings WHERE prompt_type = 'COMMON') s2), '오늘따라 유독 피곤하다
배고프다
커피 마실지 말지 고민된다
옷 사고 싶다
뭐 맛있는 거 없나
집 가고 싶다
택배 시킨 거 언제 오지
심심하다
시간이 안간다
노래 뭐 듣지
목마르다
눈이 뻑뻑하다
하품이 계속 나온다
단 거 땡긴다
뭔가 깜빡한 것 같다
유튜브나 볼까
뭔가 재밌는 거 없나
귀찮다, 손가락 하나 까딱하기 싫다
아무것도 하기 싫다
폰 어디 뒀지
일어나기 싫다
온몸이 다 무겁다
아까부터 계속 딴생각만 든다
눈이 자꾸 감긴다
안경 어디 뒀는지 모르겠다
요즘 볼 영화 없나
요즘 스팸전화가 왜 이렇게 많이 오지
폰 바꾸고 싶다
마라탕 땡긴다
로또 당첨됐으면 좋겠다', NOW(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM llm_settings s WHERE s.prompt_type = 'EONGTTUNG_TOPIC');

-- 새로 들어간 행의 v1 리비전 시딩(기존 DB에 리비전이 이미 있으면 no-op).
INSERT INTO prompt_revisions (prompt_type, version, system_prompt, saved_by, restored_from_version, created_at)
SELECT s.prompt_type, 1, s.system_prompt, NULL, NULL, s.updated_at
FROM llm_settings s
WHERE NOT EXISTS (SELECT 1 FROM prompt_revisions r WHERE r.prompt_type = s.prompt_type);
