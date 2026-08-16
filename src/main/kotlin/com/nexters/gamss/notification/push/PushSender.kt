package com.nexters.gamss.notification.push

/**
 * 기기로 알림을 보내는 통로. 구현이 FCM 이라는 사실은 이 인터페이스 밖으로 새지 않는다 —
 * 알림을 거는 쪽(배치·이벤트 리스너)은 어느 서비스로 나가는지 몰라도 된다.
 *
 * 토큰을 [String] 으로 받는 것은 일부러다. 저장 계층의 값 객체를 여기로 끌어오면 발송 통로가
 * 우리 테이블 구조에 묶인다 — 이 인터페이스가 아는 것은 "발송 대상 기기를 가리키는 문자열"까지다.
 *
 * **구현은 예외를 밖으로 던지지 않는다.** 알림 발송은 부가 기능이라, 실패가 그것을 부른 작업
 * (카드 생성 배치 등)을 멈춰 세우면 안 된다. 실패는 [PushSendResult] 로 돌려주고 로그로 남긴다.
 */
interface PushSender {
    /**
     * [tokens] 기기들에 [message] 를 보낸다. 토큰이 비어 있으면 아무것도 하지 않는다.
     *
     * 부분 실패가 정상 경로다 — 일부 토큰이 죽어 있어도 나머지는 보낸다.
     */
    fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult
}
