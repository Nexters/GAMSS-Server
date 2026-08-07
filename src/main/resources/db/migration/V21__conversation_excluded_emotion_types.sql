-- 새 채팅방 생성 시 지정한, 반응하지 않을 캐릭터 목록을 저장한다. EmotionType.name을 콤마로 이어붙인
-- 문자열로 저장하며(예: "JOY,ANGER"), 채팅방 생성 시에만 정해지고 이후에는 바뀌지 않는다.
ALTER TABLE conversations ADD COLUMN excluded_emotion_types VARCHAR(255) NULL;
