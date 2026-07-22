package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 일부러 [com.nexters.gamss.support.RepositoryTest](클래스 레벨 @Transactional로 롤백)를 상속하지
 * 않는다 — 그러면 테스트 자체의 트랜잭션이 감싸버려서, 서비스 계층 트랜잭션 없이 리포지토리를 직접
 * 호출하는 실제 운영 경로(스케줄러 등)에서 터지는 TransactionRequiredException을 재현할 수 없다.
 * (실제로 이 문제 때문에 MessageRepository의 @Modifying 메서드에 @Transactional을 명시했다.)
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class MessageRepositoryTransactionTest {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @AfterEach
    fun cleanUp() {
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `서비스 트랜잭션 없이 직접 호출해도 선점이 원자적으로 동작한다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val root = messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "일기"))

        val firstClaim =
            messageRepository.updateCommentStatus(
                root.id,
                CommentStatus.PENDING,
                listOf(CommentStatus.NONE, CommentStatus.FAILED),
                Instant.now(),
            )
        val secondClaim =
            messageRepository.updateCommentStatus(
                root.id,
                CommentStatus.PENDING,
                listOf(CommentStatus.NONE, CommentStatus.FAILED),
                Instant.now(),
            )

        assertEquals(1, firstClaim)
        assertEquals(0, secondClaim)
        assertEquals(CommentStatus.PENDING, messageRepository.findById(root.id).get().commentStatus)
    }

    @Test
    fun `FAILED 상태는 재선점된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val root =
            messageRepository.save(
                Message(
                    conversationId = conversation.id,
                    senderType = SenderType.USER,
                    content = "일기",
                    commentStatus = CommentStatus.FAILED,
                ),
            )

        val reclaimed =
            messageRepository.updateCommentStatus(
                root.id,
                CommentStatus.PENDING,
                listOf(CommentStatus.NONE, CommentStatus.FAILED),
                Instant.now(),
            )

        assertEquals(1, reclaimed)
    }

    @Test
    fun `서비스 트랜잭션 없이 직접 호출해도 오래된 PENDING이 리셋된다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val root = messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "일기"))
        messageRepository.updateCommentStatus(
            root.id,
            CommentStatus.PENDING,
            listOf(CommentStatus.NONE, CommentStatus.FAILED),
            Instant.now().minusSeconds(600),
        )

        val resetCount = messageRepository.resetStalePending(Instant.now().minusSeconds(60))

        assertEquals(1, resetCount)
        assertEquals(CommentStatus.NONE, messageRepository.findById(root.id).get().commentStatus)
    }
}
