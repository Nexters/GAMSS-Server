-- 푸시 알림을 보낼 기기(FCM 등록 토큰). 한 회원이 기기를 여러 대 쓸 수 있어 회원 1:N 토큰이고,
-- 유니크는 member_id 가 아니라 token 에 건다.
--
-- token 유니크가 필요한 이유: 같은 기기에서 A 가 로그아웃하고 B 가 로그인하면 **같은 토큰**이
-- 다른 회원으로 다시 등록된다. 유니크가 없으면 두 행이 남아 A 에게도 B 의 알림이 간다.
-- 등록은 이 제약 위에서 upsert 로 처리해 소유자를 옮긴다(DeviceTokenRepository.upsert).
--
-- 알림 수신 동의는 별도로 두지 않는다 — **행이 있으면 수신 동의**다. OS 알림 권한을 허용해야
-- 토큰이 발급되므로 등록 시점이 곧 동의 시점이고, 앱이 권한 해제·로그아웃 시 해제 API 를 호출해
-- 행을 지운다. 알림 종류가 늘거나 광고성 푸시가 붙으면 그때 별도 설정 테이블로 확장한다.
CREATE TABLE device_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
) ENGINE = InnoDB;

-- 발송 배치가 '이 회원의 모든 기기'를 조회한다.
CREATE INDEX idx_device_tokens_member_id ON device_tokens (member_id);
