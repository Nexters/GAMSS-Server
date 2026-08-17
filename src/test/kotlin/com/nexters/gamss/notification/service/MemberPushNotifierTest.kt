package com.nexters.gamss.notification.service

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.notification.domain.DeviceToken
import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.push.PushSender
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfig::class, MemberPushNotifierTest.RecordingSenderConfig::class)
@Transactional
class MemberPushNotifierTest {
    @Autowired
    private lateinit var notifier: MemberPushNotifier

    @Autowired
    private lateinit var deviceTokenService: DeviceTokenService

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var sender: RecordingPushSender

    @BeforeEach
    fun resetSender() {
        // 스프링 컨텍스트가 재사용돼 이 빈도 테스트 사이에 살아남는다. 앞선 테스트가 남긴 기록이
        // 다음 테스트의 판정을 바꾸지 않도록 매번 되돌린다.
        sender.reset()
    }

    /** 실제 FCM 대신, 받은 토큰을 기록하고 정해둔 결과를 돌려주는 구현을 세운다. */
    @TestConfiguration(proxyBeanMethods = false)
    class RecordingSenderConfig {
        @Bean
        @Primary
        fun recordingPushSender(): RecordingPushSender = RecordingPushSender()
    }

    class RecordingPushSender : PushSender {
        var sentTokens: List<String>? = null
        var invalidTokens: List<String> = emptyList()

        fun reset() {
            sentTokens = null
            invalidTokens = emptyList()
        }

        override fun send(
            tokens: List<String>,
            message: PushMessage,
        ): PushSendResult {
            sentTokens = tokens
            return PushSendResult(
                successCount = tokens.size - invalidTokens.size,
                failureCount = invalidTokens.size,
                invalidTokens = invalidTokens,
            )
        }
    }

    @Test
    fun `회원들의 모든 기기로 보낸다`() {
        val one = register("one@a.com", "token-phone", "token-tablet")
        val two = register("two@a.com", "token-two")

        notifier.send(listOf(one, two), MESSAGE)

        assertEquals(
            setOf("token-phone", "token-tablet", "token-two"),
            assertNotNull(sender.sentTokens).toSet(),
        )
    }

    @Test
    fun `등록된 기기가 없는 회원은 조용히 빠진다`() {
        val withDevice = register("with@a.com", "token-with")
        val withoutDevice = memberRepository.save(Member("without@a.com")).id

        val result = notifier.send(listOf(withDevice, withoutDevice), MESSAGE)

        assertEquals(listOf("token-with"), sender.sentTokens)
        assertEquals(1, result.successCount)
    }

    @Test
    fun `아무도 기기를 등록하지 않았으면 발송을 부르지 않는다`() {
        val member = memberRepository.save(Member("none@a.com")).id

        val result = notifier.send(listOf(member), MESSAGE)

        assertNull(sender.sentTokens)
        assertEquals(0, result.successCount)
    }

    @Test
    fun `죽은 토큰만 지우고 나머지 기기는 남긴다`() {
        val member = register("me@a.com", "token-dead", "token-alive")
        sender.invalidTokens = listOf("token-dead")

        notifier.send(listOf(member), MESSAGE)

        assertNull(deviceTokenRepository.findByToken(FcmToken("token-dead")))
        assertNotNull(deviceTokenRepository.findByToken(FcmToken("token-alive")))
    }

    /** 정리는 발송이 끝난 뒤의 일이라, 다른 회원의 멀쩡한 기기까지 건드리면 안 된다. */
    @Test
    fun `무효 토큰 정리가 다른 회원의 기기를 지우지 않는다`() {
        val target = register("target@a.com", "token-dead")
        val other = register("other@a.com", "token-other")
        sender.invalidTokens = listOf("token-dead")

        notifier.send(listOf(target, other), MESSAGE)

        assertNull(deviceTokenRepository.findByToken(FcmToken("token-dead")))
        assertNotNull(deviceTokenRepository.findByToken(FcmToken("token-other")))
    }

    @Test
    fun `같은 회원을 여러 번 넘겨도 한 번만 보낸다`() {
        val member = register("me@a.com", "token-one")

        notifier.send(listOf(member, member, member), MESSAGE)

        assertEquals(listOf("token-one"), sender.sentTokens)
    }

    @Test
    fun `무효 토큰이 없으면 아무것도 지우지 않는다`() {
        val member = register("me@a.com", "token-alive")

        notifier.send(listOf(member), MESSAGE)

        assertNotNull(deviceTokenRepository.findByToken(FcmToken("token-alive")))
    }

    /**
     * 조회·삭제 모두 `IN` 절을 500개씩 나눠 넣는다. 청크별 결과를 합치는 부분이 어긋나면 뒤쪽
     * 회원들이 조용히 빠지거나 죽은 토큰이 남는데, 작은 목록으로는 드러나지 않는다.
     */
    @Test
    fun `한 번에 넣는 개수를 넘는 대상도 나눠서 모두 처리한다`() {
        val members = memberRepository.saveAll((1..OVER_CHUNK_SIZE).map { Member("many-$it@a.com") })
        val tokens = members.map { "token-${it.id}" }
        deviceTokenRepository.saveAll(members.mapIndexed { index, m -> DeviceToken(m.id, FcmToken(tokens[index])) })
        sender.invalidTokens = tokens

        val memberIds = members.map { it.id }

        notifier.send(memberIds, MESSAGE)

        assertEquals(OVER_CHUNK_SIZE, assertNotNull(sender.sentTokens).size, "조회가 청크를 모두 합쳐야 한다")
        // 전체 행 수를 세면 커밋하는 다른 테스트가 남긴 행에 판정이 흔들린다. 이 테스트가 만든
        // 회원의 기기만 본다.
        assertEquals(
            emptyList(),
            deviceTokenRepository.findTokenValuesByMemberIdIn(memberIds),
            "삭제도 청크를 모두 돌아야 한다",
        )
    }

    private fun register(
        email: String,
        vararg tokens: String,
    ): Long {
        val member = memberRepository.save(Member(email))
        tokens.forEach { deviceTokenService.register(member.id, FcmToken(it)) }
        return member.id
    }

    companion object {
        private val MESSAGE = PushMessage(title = "제목", body = "본문")

        /** MemberPushNotifier 가 IN 절에 한 번에 넣는 개수(500)보다 하나 많게 잡는다. */
        private const val OVER_CHUNK_SIZE = 501
    }
}
