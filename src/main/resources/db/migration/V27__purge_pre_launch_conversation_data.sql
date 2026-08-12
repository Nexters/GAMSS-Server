-- 출시 전 내부 테스트로 쌓인 대화·카드·생성 이력을 비운다.
--
-- EmotionType에서 다정이(WARM)를 제거했기 때문에, 그 값으로 저장된 행이 남아 있으면
-- 읽는 순간 @Enumerated(EnumType.STRING) 역직렬화가 실패한다(카드·대화 조회, 백오피스
-- 감정 집계가 모두 이 경로다). 실사용자 배포 전이라 보존할 데이터가 없어 정리로 대응한다.
--
-- ⚠️ 다정이 행만 골라 지우면 안 된다. messages.replies_to_message_id·root_message_id가
--    사라진 메시지를 가리켜 스레드가 끊긴다(다정이 댓글 -> 유저 답글 -> 캐릭터 재응답).
--    그래서 네 테이블을 통째로 비운다 — 서로를 참조하는 컬럼이 이 안에만 있어
--    (cards.conversation_id, generation_log.conversation_id, messages의 두 참조)
--    함께 비우면 끊어진 참조가 남지 않는다.
--
-- generation_log는 감정 컬럼이 없어 역직렬화와 무관하지만 함께 비운다. 남겨두면 대화가
-- 0인데 생성 이력만 있는 상태가 되고, 대시보드 지표와 일일 토큰 상한이 이 테이블만 보기
-- 때문에 새 캐릭터 구성 기준으로 다시 쌓는 편이 해석하기 쉽다.
--
-- 회원·소셜 계정·프롬프트 설정은 남긴다(로그인과 프롬프트가 그대로 유지된다).
-- 새로 만든 환경에서는 대상 행이 없어 아무 일도 하지 않는다.

DELETE FROM messages;
DELETE FROM cards;
DELETE FROM conversations;
DELETE FROM generation_log;
