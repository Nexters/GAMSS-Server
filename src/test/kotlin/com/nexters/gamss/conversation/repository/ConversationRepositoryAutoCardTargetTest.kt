package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ConversationRepository.findAutoCardTargetIds]가 자동 카드 생성 배치의 대상만 집어오는지 검증한다.
 * 대상 선정이 틀리면 카드가 안 만들어지거나(누락) 이미 끝난 방에 헛일을 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ConversationRepositoryAutoCardTargetTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @AfterEach
    fun cleanUp() {
        conversationRepository.deleteAll()
    }

    /** 지금 만든 방들이 모두 "그 전에 생성된" 것이 되도록 넉넉히 미래로 잡은 기준 시각. */
    private val tomorrow: Instant = Instant.now().plus(1, ChronoUnit.DAYS)

    private fun markCardGenerated(conversationId: Long) {
        conversationRepository.updateCardGenerationStatus(
            conversationId,
            CardGenerationStatus.DONE,
            listOf(CardGenerationStatus.NONE),
            Instant.now(),
        )
    }

    @Test
    fun `아직 카드가 없는 진행 중 대화방과 종료된 대화방을 모두 대상으로 삼는다`() {
        val active = conversationRepository.save(Conversation(memberId = 1L))
        // 종료까지만 되고 카드 생성에서 끊긴 방 — 다음 실행이 이어서 처리해야 한다.
        val endedWithoutCard = conversationRepository.save(Conversation(memberId = 1L).apply { end() })

        val targets = conversationRepository.findAutoCardTargetIds(tomorrow)

        assertEquals(listOf(active.id, endedWithoutCard.id), targets)
    }

    @Test
    fun `이미 카드가 만들어진 대화방은 대상에서 빠진다`() {
        val done = conversationRepository.save(Conversation(memberId = 1L).apply { end() })
        markCardGenerated(done.id)
        val pending = conversationRepository.save(Conversation(memberId = 1L))

        val targets = conversationRepository.findAutoCardTargetIds(tomorrow)

        assertEquals(listOf(pending.id), targets)
    }

    @Test
    fun `삭제된 대화방은 대상에서 빠진다`() {
        conversationRepository.save(Conversation(memberId = 1L).apply { delete() })
        val alive = conversationRepository.save(Conversation(memberId = 1L))

        val targets = conversationRepository.findAutoCardTargetIds(tomorrow)

        assertEquals(listOf(alive.id), targets)
    }

    @Test
    fun `기준 시각 이후에 만들어진 대화방은 대상에서 빠진다`() {
        conversationRepository.save(Conversation(memberId = 1L))

        // 오늘 만들어진 방은 KST 오늘 자정 기준으로 아직 대상이 아니다(오늘 기록은 오늘 밤까지 열어둔다).
        val targets = conversationRepository.findAutoCardTargetIds(Instant.now().minus(1, ChronoUnit.DAYS))

        assertTrue(targets.isEmpty())
    }

    @Test
    fun `대상은 회원을 가리지 않고 모두 모은다`() {
        val mine = conversationRepository.save(Conversation(memberId = 1L))
        val others = conversationRepository.save(Conversation(memberId = 2L))

        val targets = conversationRepository.findAutoCardTargetIds(tomorrow)

        assertEquals(listOf(mine.id, others.id), targets)
    }
}
