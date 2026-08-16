-- 푸시 알림을 보낼 기기(FCM 등록 토큰). 한 회원이 기기를 여러 대 쓸 수 있어 회원 1:N 토큰이고,
-- 유니크는 member_id 가 아니라 token 에 건다.
--
-- token 유니크가 필요한 이유: FCM 토큰은 계정이 아니라 앱 설치(기기)에 붙는다. 로그인·로그아웃과
-- 무관하게 유지되고 앱을 재설치해야 새로 발급되므로, 폰 하나를 두 계정이 차례로 쓰는 상황이 생긴다
-- (QA 계정 전환, 다른 소셜 계정으로 갈아타기, 탈퇴 후 재가입). 로그아웃 때 앱이 해제 API 를 부르면
-- 정리되지만 그 호출은 빠질 수 있고, 그러면 같은 토큰이 다른 회원으로 다시 등록된다.
--
-- 유니크가 없으면 (A, 토큰)·(B, 토큰) 두 행이 남는다. 알림은 회원 앞으로 발송되므로 A 에게 보낸
-- 알림이 그 토큰으로, 즉 지금 B 가 보고 있는 화면으로 뜬다 — 알림 문구에 카드 한 줄이 들어가므로
-- 남의 감정 기록이 잠금화면에 노출된다. (member_id, token) 유니크로는 이 공존을 막지 못한다.
-- 등록은 이 제약 위에서 upsert 로 처리해 소유자를 옮긴다(DeviceTokenRepository.upsert).
--
-- 알림 수신 동의는 별도로 두지 않는다 — **행이 있으면 수신 동의**다. OS 알림 권한을 허용해야
-- 토큰이 발급되므로 등록 시점이 곧 동의 시점이고, 앱이 권한 해제·로그아웃 시 해제 API 를 호출해
-- 행을 지운다. 알림 종류가 늘거나 광고성 푸시가 붙으면 그때 별도 설정 테이블로 확장한다.
--
-- token 은 collation 을 컬럼에 못 박는다(utf8mb4_bin). 서버 기본값을 따르면 대소문자를 무시하는
-- collation 이라 대소문자만 다른 두 토큰이 **같은 유니크 키**가 되고, upsert 가 남의 행 소유자를
-- 덮어써 그 회원은 등록이 사라지고 새 회원에게는 남의 토큰이 묶인다(mysql 8.0 에서 재현 확인).
-- 실제로 그런 토큰 쌍이 생길 확률은 사실상 없지만, 토큰은 대소문자를 구분하는 불투명한 값이라
-- 언어 인식 비교를 적용할 이유 자체가 없다. 컬럼에 못 박는 것은 환경 의존을 없애는 목적도 있다 —
-- 운영은 utf8mb4_unicode_ci(compose 플래그), 테스트컨테이너는 utf8mb4_0900_ai_ci 로 서로 다르다.
CREATE TABLE device_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    token      VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
) ENGINE = InnoDB;

-- 발송 배치가 '이 회원의 모든 기기'를 조회한다.
CREATE INDEX idx_device_tokens_member_id ON device_tokens (member_id);
