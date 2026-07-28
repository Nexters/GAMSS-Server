-- 정확한 비용 계산용. 입력(프롬프트)·출력(응답) 토큰을 분리 기록한다.
-- cached_tokens 는 input_tokens 의 부분집합(캐시로 처리된 입력)이고, 출력은 캐시되지 않으므로
-- 비용 = (input-cached)×입력가 + cached×캐시가 + output×출력가 로 계산한다.
ALTER TABLE generation_log ADD COLUMN input_tokens  INT NULL;
ALTER TABLE generation_log ADD COLUMN output_tokens INT NULL;
