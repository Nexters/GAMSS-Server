-- 일일 토큰 한도의 집행용 사용량. 지금까지는 generation_log(관측용)를 회원별로 합산해 판정했는데,
-- 그 방식은 탈퇴 후 재가입으로 리셋된다 - 재가입은 새 member_id 를 받고 옛 사용량은 옛 id 에 남기 때문이다(#222).
--
-- 그래서 사용량을 '회원'이 아니라 '쿼터 주체'에 귀속시킨다. 주체는 소셜 신원에서 나오므로 재가입해도 바뀌지 않는다.
CREATE TABLE token_quota_usage (
    -- HMAC-SHA256(encryption.index-key, "<provider>:<providerId>") 를 hex 로 적은 값.
    -- 소셜 식별자 원문을 남기지 않으려는 것이다(탈퇴 시 익명화 원칙). 키 없이는 역산할 수 없다.
    subject_key  CHAR(64)    NOT NULL,
    -- 정책 리셋 시각(token_policy.reset_hour, KST) 기준 구간의 시작.
    window_start DATETIME(6) NOT NULL,
    used_tokens  BIGINT      NOT NULL DEFAULT 0,
    updated_at   DATETIME(6) NOT NULL,
    -- 판정이 PK 단건 조회로 끝나게 하는 것이 이 테이블을 따로 두는 이유다.
    -- generation_log 합산은 구간 스캔이고, 재가입이 반복되면 대상 회원 수만큼 범위가 늘어난다.
    PRIMARY KEY (subject_key, window_start)
);

-- 회원과 쿼터 주체를 잇는 매핑. 적립·판정 모두 member_id 로 들어와 주체를 찾는다.
--
-- **탈퇴 시 지우지 않는다.** social_accounts 는 (provider, provider_id) 유니크 제약 때문에 탈퇴 시
-- 하드 삭제해야 하고(AuthResourceCleaner), 그래서 지금까지는 탈퇴 전후를 이어줄 식별자가 하나도
-- 남지 않았다. 이 테이블이 그 고리다 - WithdrawnMemberCleaner 구현을 만들면 안 된다.
--
-- 대신 영구히 두지도 않는다. 막으려는 것은 '하루' 한도이므로 한 구간만 지나면 연결을 끊는다
-- (TokenQuotaRetentionScheduler). 탈퇴자를 과거 행적에 영구히 연결할 수 있는 상태로 두지 않는다.
CREATE TABLE member_social_identity (
    -- 회원당 하나. 동시 최초 로그인에서 중복 삽입이 유니크 위반으로 걸리게 PK 로 둔다.
    member_id   BIGINT      NOT NULL PRIMARY KEY,
    subject_key CHAR(64)    NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    -- 주체로 회원들을 거꾸로 찾는 경로(기동 백필, 보관기간 정리)에 쓴다.
    INDEX idx_member_social_identity_subject (subject_key)
);
