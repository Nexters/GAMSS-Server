package com.nexters.gamss.notification.service

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
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 조회 → 발송까지 실제로 이어지는지 본다. 단위 테스트는 각 조각만 보므로, 조건이 어긋나 **엉뚱한
 * 사람에게 알림이 가는 것**은 여기서만 잡힌다.
 *
 * `@Transactional` 을 쓰지 않는다 — 발송은 트랜잭션 밖에서 돌아야 하고([MemberPushNotifier] 가 그
 * 상태를 거부한다), 그 계약이 지켜지는지도 이 테스트가 함께 확인하는 셈이다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, UnfinishedConversationReminderIntegrationTest.RecordingSenderConfig::class)
class UnfinishedConversationReminderIntegrationTest {
    @Autowired
    private lateinit var reminder: UnfinishedConversationReminder

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

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

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
    fun `미종료 방을 가진 회원에게만 알린다`() {
        val unfinished = memberWith("unfinished@a.com", "token-unfinished", ended = false)
        memberWith("ended@a.com", "token-ended", ended = true)

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertEquals(listOf("token-unfinished"), sender.sentTokens)
    }

    @Test
    fun `기기를 등록하지 않은 회원만 남으면 발송하지 않는다`() {
        val member = memberRepository.save(Member("no-device@a.com"))
        saveConversation(member.id, ended = false)

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertNull(sender.sentTokens)
    }

    @Test
    fun `방이 여러 개여도 기기마다 한 번씩만 간다`() {
        val member = memberRepository.save(Member("many@a.com"))
        deviceTokenService.register(member.id, FcmToken("token-one"))
        repeat(3) { saveConversation(member.id, ended = false) }

        reminder.runFor(CREATED_AFTER, CREATED_BEFORE)

        assertEquals(listOf("token-one"), sender.sentTokens)
    }

    private fun memberWith(
        email: String,
        token: String,
        ended: Boolean,
    ): Long {
        val member = memberRepository.save(Member(email))
        deviceTokenService.register(member.id, FcmToken(token))
        saveConversation(member.id, ended)
        return member.id
    }

    private fun saveConversation(
        memberId: Long,
        ended: Boolean,
    ) {
        val conversation = conversationRepository.save(Conversation(memberId = memberId))
        if (ended) {
            conversation.end()
            conversationRepository.save(conversation)
        }
        // 리마인더는 트랜잭션 밖에서 돌아야 해서 이 테스트에 @Transactional 을 걸 수 없다.
        // 셋업의 이 갱신만 따로 트랜잭션으로 감싼다.
        transactionTemplate.execute {
            entityManager
                .createQuery("update Conversation c set c.createdAt = :createdAt where c.id = :id")
                .setParameter("createdAt", INSIDE)
                .setParameter("id", conversation.id)
                .executeUpdate()
        }
        entityManager.clear()
    }

    companion object {
        private val CREATED_AFTER: Instant = Instant.parse("2026-08-14T20:00:00Z")
        private val CREATED_BEFORE: Instant = Instant.parse("2026-08-17T20:00:00Z")
        private val INSIDE: Instant = Instant.parse("2026-08-16T00:00:00Z")
    }
}
