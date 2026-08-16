package com.nexters.gamss.notification.push

import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * 자격증명이 없는 환경(테스트·로컬)에서 어떤 구현이 서는지 고정한다.
 *
 * 여기서 FCM 구현이 서면 테스트가 실제 발송을 시도하게 되고, 자격증명이 없으면 기동 자체가
 * 실패한다. 둘 다 조용히 어긋나는 종류라 컨텍스트로 확인한다.
 */
class PushSenderSelectionTest : RepositoryTest() {
    @Autowired
    private lateinit var pushSender: PushSender

    @Test
    fun `자격증명이 없으면 보내지 않는 구현이 선택된다`() {
        assertIs<NoOpPushSender>(pushSender)
    }
}
