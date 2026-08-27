package com.nexters.gamss.notification.push

/**
 * 발송 한 번의 결과. 토큰 일부만 실패할 수 있어 성공과 실패를 건수로 돌려준다.
 *
 * [invalidTokens] 는 **더는 살아 있지 않은 토큰**이다(앱 삭제, 재설치 등). 단순 실패와 구분하는
 * 이유는 처리 방법이 다르기 때문이다. 실패는 다음 발송에서 다시 시도할 값이지만, 무효 토큰은
 * 지워야 할 값이라 그대로 두면 매번 같은 실패를 만들어낸다.
 *
 * 지우는 일 자체는 여기서 하지 않는다. 발송 어댑터가 우리 테이블을 지우는 책임까지 갖지 않도록
 * **알려주기만** 하고, 실제 삭제는 이 결과를 받은 쪽이 맡는다.
 */
data class PushSendResult(
    val successCount: Int,
    val failureCount: Int,
    val invalidTokens: List<String>,
) {
    companion object {
        /** 보낼 대상이 없었다. 알림을 끈 회원이라 **실패가 아니다.** */
        fun none(): PushSendResult = PushSendResult(successCount = 0, failureCount = 0, invalidTokens = emptyList())

        /**
         * 발송 시도가 실패했다.
         *
         * [none] 과 갈라 두는 이유는 둘을 뭉치면 "알림을 끈 회원"과 "보내려다 실패한 회원"이
         * 구분되지 않기 때문이다([com.nexters.gamss.notification.domain.NotificationOutcome] 이
         * 그 둘을 다른 값으로 남긴다).
         *
         * 실패한 토큰이 몇 개인지는 알 수 없다. 토큰을 읽기도 전에 끊겼을 수 있어서다. 회원 한 명에
         * 대한 시도가 실패했다는 뜻으로 1을 센다.
         */
        fun failed(): PushSendResult = PushSendResult(successCount = 0, failureCount = 1, invalidTokens = emptyList())
    }
}
