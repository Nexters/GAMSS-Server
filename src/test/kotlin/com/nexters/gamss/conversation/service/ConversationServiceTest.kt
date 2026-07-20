package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationServiceTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val properties = ConversationProperties(dayStartTime = LocalTime.of(6, 0))
    private val conversationService = ConversationService(conversationRepository, messageRepository, properties)

    @Test
    fun `conversationId 없이 저장하면 새 채팅방을 만들어 메시지를 담는다`() {
        every { conversationRepository.save(any()) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }

        val message = conversationService.saveUserMessage(1L, null, "오늘 억울한 일이 있었어")

        assertEquals(SenderType.USER, message.senderType)
        assertEquals("오늘 억울한 일이 있었어", message.content)
        verify(exactly = 1) { conversationRepository.save(any()) }
    }

    @Test
    fun `conversationId가 있으면 기존 채팅방에 이어서 저장한다`() {
        val conversation = Conversation(memberId = 1L)
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every { messageRepository.save(any()) } answers { firstArg() }

        conversationService.saveUserMessage(1L, 10L, "이어서 쓰는 말")

        verify(exactly = 0) { conversationRepository.save(any()) }
        verify(exactly = 1) { messageRepository.save(any()) }
    }

    @Test
    fun `없는 채팅방에 저장하면 CONVERSATION_NOT_FOUND`() {
        every { conversationRepository.findById(99L) } returns Optional.empty()

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 99L, "내용")
            }

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `남의 채팅방에 저장하면 CONVERSATION_ACCESS_DENIED`() {
        val conversation = Conversation(memberId = 2L)
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "내용")
            }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `같은 채팅방의 메시지에 답장으로 저장한다`() {
        val conversation = Conversation(memberId = 1L)
        val target = Message(conversationId = 0L, senderType = SenderType.USER, content = "원본")
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every { messageRepository.findById(3L) } returns Optional.of(target)
        every { messageRepository.save(any()) } answers { firstArg() }

        val message = conversationService.saveUserMessage(1L, 10L, "답장이야", repliesToMessageId = 3L)

        assertEquals(3L, message.repliesToMessageId)
    }

    @Test
    fun `새 채팅방을 만들면서 답장하면 INVALID_INPUT`() {
        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, null, "내용", repliesToMessageId = 3L)
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `없는 메시지에 답장하면 INVALID_INPUT`() {
        val conversation = Conversation(memberId = 1L)
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every { messageRepository.findById(99L) } returns Optional.empty()

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "내용", repliesToMessageId = 99L)
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `다른 채팅방의 메시지에 답장하면 INVALID_INPUT`() {
        val conversation = Conversation(memberId = 1L)
        val other = Message(conversationId = 999L, senderType = SenderType.USER, content = "딴 방 메시지")
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every { messageRepository.findById(3L) } returns Optional.of(other)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "내용", repliesToMessageId = 3L)
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `날짜 조회는 dayStartTime(06시, KST) 경계로 범위를 계산한다`() {
        val start = slot<Instant>()
        val end = slot<Instant>()
        every {
            conversationRepository.findAllByMemberIdAndCreatedAtInRange(1L, capture(start), capture(end))
        } returns emptyList()

        conversationService.getConversations(1L, LocalDate.of(2026, 7, 19))

        // KST 2026-07-19 06:00 = UTC 2026-07-18 21:00
        assertEquals(Instant.parse("2026-07-18T21:00:00Z"), start.captured)
        assertEquals(Instant.parse("2026-07-19T21:00:00Z"), end.captured)
    }

    @Test
    fun `채팅방 메시지를 조회한다`() {
        val conversation = Conversation(memberId = 1L)
        val messages = listOf(Message(conversationId = 10L, senderType = SenderType.USER, content = "안녕"))
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every { messageRepository.findAllByConversationIdOrderByIdAsc(10L) } returns messages

        assertEquals(messages, conversationService.getMessages(1L, 10L))
    }

    @Test
    fun `남의 채팅방 메시지를 조회하면 CONVERSATION_ACCESS_DENIED`() {
        val conversation = Conversation(memberId = 2L)
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.getMessages(1L, 10L)
            }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }
}
