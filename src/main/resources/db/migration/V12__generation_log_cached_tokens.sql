-- 컨텍스트 캐싱 효과 측정용. usageMetadata.cachedContentTokenCount(캐시로 처리돼 할인 과금되는 입력 토큰)를
-- 기록한다. total_tokens(=used_tokens)는 캐싱으로 줄지 않으므로, 실효(과금) 토큰은 이 값으로 계산한다.
ALTER TABLE generation_log ADD COLUMN cached_tokens INT NULL;
