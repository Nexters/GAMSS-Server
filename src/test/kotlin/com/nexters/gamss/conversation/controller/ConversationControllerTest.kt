package com.nexters.gamss.conversation.controller

import com.nexters.gamss.conversation.controller.dto.SaveMessageRequest
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.service.CommentGenerationOutcome
import com.nexters.gamss.conversation.service.CommentGenerationService
import com.nexters.gamss.conversation.service.ConversationSearchService
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.conversation.service.GenerationResult
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.security.AuthPrincipal
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `POST /messages`가 저장 직후 같은 요청 안에서 [CommentGenerationService.generateFor]를 호출해
 * 결과를 응답에 매핑하는지(#39)를 검증한다. 일기/답글 중 어느 흐름을 탈지는 더 이상 컨트롤러가
 * 아니라 서비스가 message 자체로 판단하므로, 여기서는 라우팅이 아니라 "저장 결과 + 생성 결과를
 * 올바르게 잇는지"만 확인한다. 라우팅과 CAS·재시도·실패 처리 자체는 서비스 단위 테스트
 * ([com.nexters.gamss.conversation.service.CommentGenerationServiceTest])가 이미 다룬다.
 */
class ConversationControllerTest {
    private val conversationService = mockk<ConversationService>()
    private val commentGenerationService = mockk<CommentGenerationService>()

    // 검색은 이 테스트의 관심사가 아니라 생성자만 채운다(검증은 ConversationSearchServiceTest).
    private val conversationSearchService = mockk<ConversationSearchService>()
    private val controller =
        ConversationController(conversationService, commentGenerationService, conversationSearchService)

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
    fun `일기를 저장하면 generateFor 결과의 comments를 그대로 응답에 담는다`() {
        val saved = message(id = 5L)
        val comments = listOf(characterReply(id = 6L), characterReply(id = 7L))
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = null, content = "오늘 있었던 일", repliesToMessageId = null)
        } returns saved
        every { commentGenerationService.generateFor(1L, saved) } returns
            GenerationResult(CommentGenerationOutcome.DONE, comments, usedTokens = 42)

        val response = controller.saveMessage(principal, SaveMessageRequest(content = "오늘 있었던 일"))

        assertEquals(5L, response.data!!.message.id)
        assertEquals("DONE", response.data!!.commentStatus.name)
        assertEquals(2, response.data!!.comments.size)
        assertEquals(42, response.data!!.usedTokens)
    }

    @Test
    fun `답장을 저장하면 generateFor 결과의 comments를 그대로 응답에 담는다`() {
        val saved = message(id = 8L, repliesToMessageId = 5L)
        val reply = characterReply(id = 9L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = 10L, content = "답장", repliesToMessageId = 5L)
        } returns saved
        every { commentGenerationService.generateFor(1L, saved) } returns
            GenerationResult(CommentGenerationOutcome.DONE, listOf(reply), usedTokens = 7)

        val response =
            controller.saveMessage(principal, SaveMessageRequest(conversationId = 10L, content = "답장", repliesToMessageId = 5L))

        assertEquals(8L, response.data!!.message.id)
        assertEquals("DONE", response.data!!.commentStatus.name)
        assertEquals(listOf(9L), response.data!!.comments.map { it.id })
        assertEquals(7, response.data!!.usedTokens)
    }

    @Test
    fun `생성이 실패해도 저장 결과는 유지되고 commentStatus=FAILED, comments는 빈 리스트로 반환된다`() {
        val saved = message(id = 5L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = null, content = "오늘 있었던 일", repliesToMessageId = null)
        } returns saved
        every { commentGenerationService.generateFor(1L, saved) } returns GenerationResult(CommentGenerationOutcome.FAILED)

        val response = controller.saveMessage(principal, SaveMessageRequest(content = "오늘 있었던 일"))

        assertEquals(5L, response.data!!.message.id)
        assertEquals("FAILED", response.data!!.commentStatus.name)
        assertTrue(response.data!!.comments.isEmpty())
        assertEquals(null, response.data!!.usedTokens)
    }

    @Test
    fun `생성이 GENERATING을 반환하면 예외를 던진다`() {
        val saved = message(id = 5L)
        every {
            conversationService.saveUserMessage(memberId = 1L, conversationId = null, content = "오늘 있었던 일", repliesToMessageId = null)
        } returns saved
        every { commentGenerationService.generateFor(1L, saved) } returns GenerationResult(CommentGenerationOutcome.GENERATING)

        assertFailsWith<IllegalStateException> {
            controller.saveMessage(principal, SaveMessageRequest(content = "오늘 있었던 일"))
        }
    }
}
