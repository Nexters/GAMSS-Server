-- 일일 토큰 한도의 집행용 사용량(#222).
--
-- 지금까지는 generation_log 를 회원별로 합산해 판정했는데, 그 방식은 탈퇴 후 재가입으로 리셋된다 -
-- 재가입은 새 member_id 를 받고 옛 사용량은 옛 id 에 남기 때문이다. 그래서 사용량을 회원이 아니라
-- 소셜 신원에서 나온 '쿼터 주체'에 귀속시킨다.
CREATE TABLE token_quota_usage (
    -- HMAC-SHA256(encryption.index-key, "<provider>:<providerId>") 의 hex. 소셜 식별자 원문을 남기지 않는다.
    subject_key  CHAR(64)    NOT NULL,
    -- 정책 리셋 시각(token_policy.reset_hour, KST) 기준 구간의 시작.
    window_start DATETIME(6) NOT NULL,
    used_tokens  BIGINT      NOT NULL DEFAULT 0,
    updated_at   DATETIME(6) NOT NULL,
    -- 판정이 PK 단건 조회로 끝나게 하는 것이 이 테이블을 따로 두는 이유다.
    PRIMARY KEY (subject_key, window_start)
);

-- 회원과 쿼터 주체를 잇는 색인. 적립·판정 모두 member_id 로 들어와 주체를 찾는다.
-- 탈퇴 시 지운다(MemberSocialIdentityCleaner) - subject_key 가 결정론적이라 재가입이 같은 키로 다시 잇는다.
CREATE TABLE member_social_identity (
    -- 회원당 하나. 동시 최초 로그인의 중복 삽입이 유니크 위반으로 걸리게 PK 로 둔다.
    member_id   BIGINT      NOT NULL PRIMARY KEY,
    subject_key CHAR(64)    NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    -- 주체로 묶어 집계하는 경로(기동 백필)에 쓴다.
    INDEX idx_member_social_identity_subject (subject_key)
);
