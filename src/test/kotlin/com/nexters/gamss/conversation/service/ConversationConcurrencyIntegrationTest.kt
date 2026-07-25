package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 삭제와 상태 변경(메시지 저장) 요청이 동시에 들어와도 순서가 보장되는지 검증한다.
 *
 * getOwnedConversationForUpdate가 PESSIMISTIC_WRITE로 행을 잠그므로, 삭제 트랜잭션이 커밋될
 * 때까지 뒤이은 메시지 저장은 반드시 블로킹돼야 하고, 깨어난 뒤에는 DELETED 상태를 다시 읽어
 * CONVERSATION_ALREADY_DELETED로 실패해야 한다. 락이 없다면 저장이 삭제보다 먼저 ACTIVE를
 * 읽고 삭제 커밋 이후에 메시지를 써버리는 경합이 가능하다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ConversationConcurrencyIntegrationTest {
    @Autowired
    private lateinit var conversationService: ConversationService

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @AfterEach
    fun cleanUp() {
        // 실제 커밋으로 락 경합을 재현해야 해서 @Transactional 롤백을 쓸 수 없으므로 직접 정리한다.
        messageRepository.deleteAll()
        conversationRepository.deleteAll()
    }

    @Test
    fun `삭제 트랜잭션이 진행 중이면 동시 메시지 저장은 대기했다가 삭제 후 상태로 실패한다`() {
        val conversation = conversationRepository.save(Conversation(memberId = 1L))
        val conversationId = conversation.id
        val transactionTemplate = TransactionTemplate(transactionManager)

        val lockAcquired = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        val deleteFuture =
            executor.submit {
                transactionTemplate.execute {
                    val locked = conversationRepository.findByIdForUpdate(conversationId).orElseThrow()
                    lockAcquired.countDown()
                    // 락을 쥔 채로 대기 — 동시에 들어오는 메시지 저장이 반드시 이 트랜잭션 종료를 기다리게 만든다.
                    Thread.sleep(500)
                    locked.delete()
                }
            }

        assertTrue(lockAcquired.await(5, TimeUnit.SECONDS), "삭제 트랜잭션이 락을 잡지 못했다")

        val saveStartNanos = System.nanoTime()
        val saveFuture =
            executor.submit {
                conversationService.saveUserMessage(1L, conversationId, "삭제 경합 중 저장 시도")
            }

        deleteFuture.get(10, TimeUnit.SECONDS)

        val executionException =
            assertFailsWith<ExecutionException> {
                saveFuture.get(10, TimeUnit.SECONDS)
            }
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - saveStartNanos)
        executor.shutdown()

        val cause = executionException.cause
        assertTrue(cause is BusinessException, "삭제된 채팅방 저장 시도는 BusinessException이어야 한다: $cause")
        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, (cause as BusinessException).errorCode)
        assertTrue(elapsedMillis >= 400, "삭제 트랜잭션의 락이 풀릴 때까지 대기했어야 한다 (실제: ${elapsedMillis}ms)")
        assertEquals(0, messageRepository.findAllByConversationIdOrderByIdAsc(conversationId).size, "삭제된 채팅방에는 메시지가 남지 않아야 한다")
    }
}
