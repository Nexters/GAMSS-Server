package com.nexters.gamss.conversation.controller

import com.nexters.gamss.conversation.controller.dto.SaveMessageRequest
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.service.CommentGenerationOutcome
import com.nexters.gamss.conversation.service.CommentGenerationResult
import com.nexters.gamss.conversation.service.CommentGenerationService
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.conversation.service.ReplyGenerationResult
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.security.AuthPrincipal
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `POST /messages`가 저장 직후 같은 요청 안에서 [CommentGenerationService]를 호출하는지(#39)를
 * 검증한다. Spring 컨텍스트 없이 순수 mockk로, 실제 저장/생성 로직은 각 서비스의 자체 단위
 * 테스트([com.nexters.gamss.conversation.service.CommentGenerationServiceTest])가 이미 다룬다.
 */
class ConversationControllerTest {
    private val conversationService = mockk<ConversationService>()
    private val commentGenerationService = mockk<CommentGenerationService>()
    private val controller = ConversationController(conversationService, commentGenerationService)

    private val principal = AuthPrincipal(memberId = 1L)

    private fun message(
        id: Long,
        repliesToMessageId: Long? = null,
    ): Message =
        Message(
            conversationId = 10L,
            senderType = SenderType.USER,
            content = "오늘 있었던 일",
            repliesToMessageId = repliesToMessageId,
        ).also { ReflectionTestUtils.setField(it, "id", id) }

    private fun characterReply(id: Long): Message =
        Message(
            conversationId = 10L,
            senderType = SenderType.CHARACTER,
            emotionType = EmotionType.JOY,
            content = "그랬구나!",
            repliesToMessageId = 5L,
        ).also { ReflectionTestUtils.setField(it, "id", id) }

    @Test
    fun `답장이 아니면 저장 후 generateComments를 호출하고 comments를 반환한다`() {
        val saved = message(id = 5L)
        val comments = listOf(characterReply(id = 6L), characterReply(id = 7L))
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = null, content = "오늘 있었던 일", repliesToMessageId = null)
        } returns saved
        every { commentGenerationService.generateComments(1L, 5L) } returns
            CommentGenerationResult(CommentGenerationOutcome.DONE, comments, usedTokens = 42)

        val response = controller.saveMessage(principal, SaveMessageRequest(content = "오늘 있었던 일"))

        assertEquals(5L, response.data!!.message.id)
        assertEquals("DONE", response.data!!.commentStatus.name)
        assertEquals(2, response.data!!.comments.size)
        assertEquals(42, response.data!!.usedTokens)
        verify(exactly = 0) { commentGenerationService.generateReplyComment(any(), any()) }
    }

    @Test
    fun `답장이면 저장 후 generateReplyComment를 호출하고 comments에 1건을 반환한다`() {
        val saved = message(id = 8L, repliesToMessageId = 5L)
        val reply = characterReply(id = 9L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = 10L, content = "답장", repliesToMessageId = 5L)
        } returns saved
        every { commentGenerationService.generateReplyComment(1L, 8L) } returns
            ReplyGenerationResult(CommentGenerationOutcome.DONE, reply, usedTokens = 7)

        val response =
            controller.saveMessage(principal, SaveMessageRequest(conversationId = 10L, content = "답장", repliesToMessageId = 5L))

        assertEquals(8L, response.data!!.message.id)
        assertEquals("DONE", response.data!!.commentStatus.name)
        assertEquals(listOf(9L), response.data!!.comments.map { it.id })
        assertEquals(7, response.data!!.usedTokens)
        verify(exactly = 0) { commentGenerationService.generateComments(any(), any()) }
    }

    @Test
    fun `생성이 실패해도 저장 결과는 유지되고 commentStatus=FAILED, comments는 빈 리스트로 반환된다`() {
        val saved = message(id = 5L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = null, content = "오늘 있었던 일", repliesToMessageId = null)
        } returns saved
        every { commentGenerationService.generateComments(1L, 5L) } returns CommentGenerationResult(CommentGenerationOutcome.FAILED)

        val response = controller.saveMessage(principal, SaveMessageRequest(content = "오늘 있었던 일"))

        assertEquals(5L, response.data!!.message.id)
        assertEquals("FAILED", response.data!!.commentStatus.name)
        assertTrue(response.data!!.comments.isEmpty())
        assertEquals(null, response.data!!.usedTokens)
    }

    @Test
    fun `방금 저장한 메시지의 댓글 생성이 GENERATING을 반환하면 예외를 던진다`() {
        val saved = message(id = 5L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = null, content = "오늘 있었던 일", repliesToMessageId = null)
        } returns saved
        every { commentGenerationService.generateComments(1L, 5L) } returns CommentGenerationResult(CommentGenerationOutcome.GENERATING)

        assertFailsWith<IllegalStateException> {
            controller.saveMessage(principal, SaveMessageRequest(content = "오늘 있었던 일"))
        }
    }

    @Test
    fun `방금 저장한 답글의 재응답 생성이 GENERATING을 반환하면 예외를 던진다`() {
        val saved = message(id = 8L, repliesToMessageId = 5L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = 10L, content = "답장", repliesToMessageId = 5L)
        } returns saved
        every { commentGenerationService.generateReplyComment(1L, 8L) } returns ReplyGenerationResult(CommentGenerationOutcome.GENERATING)

        assertFailsWith<IllegalStateException> {
            controller.saveMessage(principal, SaveMessageRequest(conversationId = 10L, content = "답장", repliesToMessageId = 5L))
        }
    }
}
