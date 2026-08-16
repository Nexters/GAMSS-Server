package com.nexters.gamss.notification.push

import org.slf4j.LoggerFactory

/**
 * 자격증명이 없는 환경(로컬·테스트)에서 쓰는 구현. 실제로 보내지 않고 로그만 남긴다.
 *
 * 발송을 부르는 쪽이 환경을 따지지 않게 하려고 빈이 아예 없는 대신 이 구현을 세운다 — 그러지
 * 않으면 알림을 거는 모든 자리에 "자격증명이 있으면"이라는 분기가 하나씩 생긴다.
 *
 * 보내지 않았으므로 성공으로 세지 않는다([PushSendResult.none]). 성공 건수로 돌려주면 로컬
 * 로그와 지표가 실제로 나가지도 않은 알림을 보냈다고 말하게 된다.
 */
class NoOpPushSender : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushSendResult {
        log.info("푸시 발송 생략(자격증명 없음): 대상={}건, 제목={}", tokens.size, message.title)
        return PushSendResult.none()
    }
}
