package com.nexters.gamss.notification.service

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.push.PushMessage
import com.nexters.gamss.notification.push.PushSendResult
import com.nexters.gamss.notification.push.PushSender
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 조회 → 발송까지 실제로 이어지는지 본다. 카드를 못 받은 사람에게 "카드가 도착했어요"가 가는 것은
 * 단위 테스트로는 드러나지 않는다.
 *
 * `@Transactional` 을 쓰지 않는다 — 발송은 트랜잭션 밖에서 돌아야 하고([MemberPushNotifier] 가 그
 * 상태를 거부한다), 그 계약이 지켜지는지도 함께 확인하는 셈이다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, CardCreatedNotifierIntegrationTest.RecordingSenderConfig::class)
class CardCreatedNotifierIntegrationTest {
    @Autowired
    private lateinit var cardCreatedNotifier: CardCreatedNotifier

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var deviceTokenService: DeviceTokenService

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var sender: RecordingPushSender

    @TestConfiguration(proxyBeanMethods = false)
    class RecordingSenderConfig {
        @Bean
        @Primary
        fun recordingPushSender(): RecordingPushSender = RecordingPushSender()
    }

    class RecordingPushSender : PushSender {
        var sentTokens: List<String>? = null

        fun reset() {
            sentTokens = null
        }

        override fun send(
            tokens: List<String>,
            message: PushMessage,
        ): PushSendResult {
            sentTokens = tokens
            return PushSendResult(successCount = tokens.size, failureCount = 0, invalidTokens = emptyList())
        }
    }

    @BeforeEach
    fun resetSender() {
        sender.reset()
    }

    @AfterEach
    fun cleanUp() {
        deviceTokenRepository.deleteAll()
        conversationRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `카드를 받은 회원에게만 알린다`() {
        memberWith("done@a.com", "token-done", CardGenerationStatus.DONE)
        memberWith("skipped@a.com", "token-skipped", CardGenerationStatus.SKIPPED)
        memberWith("failed@a.com", "token-failed", CardGenerationStatus.FAILED)

        cardCreatedNotifier.runSince(SINCE)

        assertEquals(listOf("token-done"), sender.sentTokens)
    }

    @Test
    fun `아무도 카드를 못 받았으면 발송하지 않는다`() {
        memberWith("skipped@a.com", "token-skipped", CardGenerationStatus.SKIPPED)

        cardCreatedNotifier.runSince(SINCE)

        assertNull(sender.sentTokens)
    }

    @Test
    fun `기기를 등록하지 않은 회원만 남으면 발송하지 않는다`() {
        val member = memberRepository.save(Member("no-device@a.com"))
        saveConversation(member.id, CardGenerationStatus.DONE)

        cardCreatedNotifier.runSince(SINCE)

        assertNull(sender.sentTokens)
    }

    private fun memberWith(
        email: String,
        token: String,
        status: CardGenerationStatus,
    ) {
        val member = memberRepository.save(Member(email))
        deviceTokenService.register(member.id, FcmToken(token))
        saveConversation(member.id, status)
    }

    private fun saveConversation(
        memberId: Long,
        status: CardGenerationStatus,
    ) {
        val conversation = conversationRepository.save(Conversation(memberId = memberId))
        conversationRepository.updateCardGenerationStatus(
            conversation.id,
            status,
            CardGenerationStatus.entries,
            AFTER_SINCE,
        )
    }

    companion object {
        private val SINCE: Instant = Instant.parse("2026-08-18T20:00:00Z")
        private val AFTER_SINCE: Instant = Instant.parse("2026-08-18T20:00:30Z")
    }
}
