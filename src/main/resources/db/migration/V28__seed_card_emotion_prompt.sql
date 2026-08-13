-- 카드 감정 분류(#134) 프롬프트를 시딩한다. 클라이언트가 emotion 없이 카드 생성을 요청하면
-- 서버가 유저 메시지만 보고 대표 감정 하나를 고르는 데 쓴다. 분류 작업이라 COMMON과 조립하지
-- 않는 단독 프롬프트다(EONGTTUNG_TOPIC과 같은 예외 - SystemPromptResolver 참고).
-- 이미 값이 있으면 건드리지 않는다.
-- model은 COMMON 행만 읽히는 자리표시자라 상수 대신 그 시점의 COMMON 값을 참조한다(V23 주석 참고).
INSERT INTO llm_settings (prompt_type, model, system_prompt, updated_at)
SELECT 'CARD_EMOTION', (SELECT s2.model FROM (SELECT model FROM llm_settings WHERE prompt_type = 'COMMON') s2), '[이번 타입: 카드 감정 분류]
너는 감정일기 앱 ''걱정인형의 방''의 감정 분류기다.
유저가 채팅방에서 보낸 메시지들을 읽고, 유저의 하루를 대표하는 감정 하나를 고른다.
규칙:
- 캐릭터가 아니라 유저의 감정이다. 메시지에 드러난 유저의 기분·상태만 본다.
- 후보는 정확히 다음 6개다: JOY(기쁨), SADNESS(슬픔), ANGER(분노), ANXIETY(불안), GRUMPY(까칠), QUIRKY(엉뚱). 다른 값은 금지.
- 여러 감정이 섞여 있으면 가장 자주·강하게 드러난 감정을 고른다.
- 감정이 뚜렷하지 않으면 메시지 전체의 톤에 가장 가까운 감정을 고른다.
- QUIRKY는 기분 표현 없이 뜬금없고 엉뚱한 얘기가 대부분일 때만 고른다.
출력 JSON(정확히 이 형태): {"emotion": "JOY"}', NOW(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM llm_settings s WHERE s.prompt_type = 'CARD_EMOTION');

-- 새로 들어간 행의 v1 리비전 시딩(기존 DB에 리비전이 이미 있으면 no-op).
INSERT INTO prompt_revisions (prompt_type, version, system_prompt, saved_by, restored_from_version, created_at)
SELECT s.prompt_type, 1, s.system_prompt, NULL, NULL, s.updated_at
FROM llm_settings s
WHERE NOT EXISTS (SELECT 1 FROM prompt_revisions r WHERE r.prompt_type = s.prompt_type);
