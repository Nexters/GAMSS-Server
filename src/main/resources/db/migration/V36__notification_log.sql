-- 새벽 배치가 건 푸시 알림 1건당, 그 알림의 대상이 된 대화방마다 한 줄.
--
-- 지금까지 발송 이력을 어디에도 남기지 않아 "알림이 나갔는지"를 서버 로그로만 확인할 수 있었다.
-- 로그는 회원 단위 집계라 "이 방 때문에 대상이었는가"를 답하지 못한다.
--
-- **발송은 회원 단위인데 이 표는 대화방 단위다.** 04:30 리마인더는 미종료 방을 가진 회원에게
-- 회원당 한 번 나가고, 05:00 카드 알림도 한 회원이 방을 여러 개 만들어도 첫 카드에 한 번만 나간다.
-- 발송 한 번이 방 여러 개를 커버하면 방마다 한 줄씩 남기고 같은 결과를 적는다. 백오피스가
-- 대화방별로 보여줘야 하는데 회원 단위로만 남기면 그 매핑을 만들 수 없다.
--
-- outcome 을 성공/실패 두 값으로 두지 않는다. 알림을 끈 회원(NO_DEVICE)은 실패가 아니라 정상이고,
-- 같은 회원의 다른 방으로 이미 나가서 건너뛴 것(SKIPPED)도 실패가 아니다. 셋을 뭉치면 백오피스에서
-- '알림이 안 갔다'로만 보여 대응할 것과 아닌 것이 구분되지 않는다.
CREATE TABLE notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT      NOT NULL,          -- 발송 대상 회원
    conversation_id BIGINT      NOT NULL,          -- 이 알림의 대상이 된 대화방
    type            VARCHAR(30) NOT NULL,          -- UNFINISHED_REMINDER(04:30) | CARD_CREATED(05:00)
    outcome         VARCHAR(20) NOT NULL,          -- SENT | NO_DEVICE | FAILED | SKIPPED
    created_at      DATETIME(6) NOT NULL
);

-- 백오피스 대화방별 사용량은 한 페이지(20행)의 방 id 들로 한 번에 조회한다.
-- type 을 함께 넣어, 방 하나에 대해 종류별 최신 한 줄을 뽑는 조회가 인덱스만 타게 한다.
CREATE INDEX idx_notification_log_conversation ON notification_log (conversation_id, type, created_at);
