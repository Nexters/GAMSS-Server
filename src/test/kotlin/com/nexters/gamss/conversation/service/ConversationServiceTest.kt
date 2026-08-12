package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationServiceTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val conversationService = ConversationService(conversationRepository, messageRepository)

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
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)
        every { messageRepository.save(any()) } answers { firstArg() }

        conversationService.saveUserMessage(1L, 10L, "이어서 쓰는 말")

        verify(exactly = 0) { conversationRepository.save(any()) }
        verify(exactly = 1) { messageRepository.save(any()) }
    }

    @Test
    fun `excludeCharacters를 지정하면 새 채팅방의 제외 목록으로 저장된다`() {
        val savedConversation = slot<Conversation>()
        every { conversationRepository.save(capture(savedConversation)) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }

        conversationService.saveUserMessage(
            1L,
            null,
            "오늘 억울한 일이 있었어",
            excludeCharacters = listOf(EmotionType.ANGER, EmotionType.ANXIETY),
        )

        assertEquals(listOf(EmotionType.ANGER, EmotionType.ANXIETY), savedConversation.captured.excludedEmotionTypes.values)
    }

    @Test
    fun `excludeCharacters에 중복이 있으면 중복을 제거하고 저장한다`() {
        val savedConversation = slot<Conversation>()
        every { conversationRepository.save(capture(savedConversation)) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }

        conversationService.saveUserMessage(
            1L,
            null,
            "내용",
            excludeCharacters = listOf(EmotionType.ANGER, EmotionType.ANGER, EmotionType.ANXIETY),
        )

        assertEquals(listOf(EmotionType.ANGER, EmotionType.ANXIETY), savedConversation.captured.excludedEmotionTypes.values)
    }

    @Test
    fun `excludeCharacters로 전체 캐릭터를 제외하면 INVALID_INPUT`() {
        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, null, "내용", excludeCharacters = EmotionType.SELECTABLE)
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verify(exactly = 0) { conversationRepository.save(any()) }
    }

    @Test
    fun `excludeCharacters로 5종(1종만 남기고)까지는 제외할 수 있다`() {
        val savedConversation = slot<Conversation>()
        every { conversationRepository.save(capture(savedConversation)) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }
        val excludeFiveTypes = EmotionType.SELECTABLE.drop(1)

        conversationService.saveUserMessage(1L, null, "내용", excludeCharacters = excludeFiveTypes)

        assertEquals(excludeFiveTypes, savedConversation.captured.excludedEmotionTypes.values)
    }

    @Test
    fun `구버전 앱이 다정이를 제외 목록에 보내도 무시하고 저장한다`() {
        val savedConversation = slot<Conversation>()
        every { conversationRepository.save(capture(savedConversation)) } answers { firstArg() }
        every { messageRepository.save(any()) } answers { firstArg() }

        conversationService.saveUserMessage(1L, null, "내용", excludeCharacters = listOf(EmotionType.WARM, EmotionType.ANGER))

        assertEquals(listOf(EmotionType.ANGER), savedConversation.captured.excludedEmotionTypes.values)
    }

    @Test
    fun `기존 채팅방에 이어서 보낼 때 excludeCharacters를 보내도 무시된다`() {
        val conversation = Conversation(memberId = 1L)
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)
        every { messageRepository.save(any()) } answers { firstArg() }

        conversationService.saveUserMessage(1L, 10L, "이어서 쓰는 말", excludeCharacters = listOf(EmotionType.ANGER))

        assertEquals(emptyList(), conversation.excludedEmotionTypes.values)
        verify(exactly = 0) { conversationRepository.save(any()) }
    }

    @Test
    fun `없는 채팅방에 저장하면 CONVERSATION_NOT_FOUND`() {
        every { conversationRepository.findByIdForUpdate(99L) } returns Optional.empty()

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 99L, "내용")
            }

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `남의 채팅방에 저장하면 CONVERSATION_ACCESS_DENIED`() {
        val conversation = Conversation(memberId = 2L)
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "내용")
            }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `같은 채팅방의 캐릭터 댓글에 답장으로 저장한다`() {
        val conversation = Conversation(memberId = 1L)
        val target =
            Message(
                conversationId = 0L,
                senderType = SenderType.CHARACTER,
                emotionType = EmotionType.JOY,
                content = "댓글",
            )
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)
        every { messageRepository.findById(3L) } returns Optional.of(target)
        every { messageRepository.save(any()) } answers { firstArg() }

        val message = conversationService.saveUserMessage(1L, 10L, "답장이야", repliesToMessageId = 3L)

        assertEquals(3L, message.repliesToMessageId)
    }

    @Test
    fun `캐릭터 메시지가 아닌 대상에 답장하면 INVALID_INPUT`() {
        val conversation = Conversation(memberId = 1L)
        val target = Message(conversationId = 10L, senderType = SenderType.USER, content = "다른 유저 메시지 아님, 같은 방의 일기")
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)
        every { messageRepository.findById(3L) } returns Optional.of(target)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "답장이야", repliesToMessageId = 3L)
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verify(exactly = 0) { messageRepository.save(any()) }
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
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)
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
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)
        every { messageRepository.findById(3L) } returns Optional.of(other)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "내용", repliesToMessageId = 3L)
            }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `날짜 조회는 KST 달력 날짜(자정~자정)로 범위를 계산한다`() {
        val start = slot<Instant>()
        val end = slot<Instant>()
        every {
            conversationRepository.findAllByMemberIdAndCreatedAtInRange(1L, capture(start), capture(end))
        } returns emptyList()

        conversationService.getConversations(1L, LocalDate.of(2026, 7, 19))

        // KST 2026-07-19 00:00 = UTC 2026-07-18 15:00
        assertEquals(Instant.parse("2026-07-18T15:00:00Z"), start.captured)
        assertEquals(Instant.parse("2026-07-19T15:00:00Z"), end.captured)
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
    fun `채팅방 메시지 조회는 티키타카를 답장 대상 댓글 바로 다음으로 재배치한다`() {
        val conversation = Conversation(memberId = 1L)
        val comment =
            Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "댓글")
                .also { ReflectionTestUtils.setField(it, "id", 1L) }
        val otherComment =
            Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.ANGER, content = "댓글2")
                .also { ReflectionTestUtils.setField(it, "id", 2L) }
        val tikitaka =
            Message(
                conversationId = 10L,
                senderType = SenderType.CHARACTER,
                emotionType = EmotionType.ANXIETY,
                content = "티키타카",
                repliesToMessageId = 1L,
            ).also { ReflectionTestUtils.setField(it, "id", 3L) }
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every { messageRepository.findAllByConversationIdOrderByIdAsc(10L) } returns listOf(comment, otherComment, tikitaka)

        val messages = conversationService.getMessages(1L, 10L)

        assertEquals(listOf(1L, 3L, 2L), messages.map { it.id })
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

    @Test
    fun `채팅방을 종료하면 상태가 ENDED가 된다`() {
        val conversation = Conversation(memberId = 1L)
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val ended = conversationService.endConversation(1L, 10L)

        assertEquals(ConversationStatus.ENDED, ended.status)
    }

    @Test
    fun `이미 종료된 채팅방을 다시 종료하면 CONVERSATION_ALREADY_ENDED`() {
        val conversation = Conversation(memberId = 1L).apply { end() }
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.endConversation(1L, 10L)
            }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_ENDED, exception.errorCode)
    }

    @Test
    fun `종료된 채팅방에 메시지를 저장하면 CONVERSATION_ENDED`() {
        val conversation = Conversation(memberId = 1L).apply { end() }
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "종료된 방에 쓰기")
            }

        assertEquals(ErrorCode.CONVERSATION_ENDED, exception.errorCode)
    }

    @Test
    fun `채팅방을 삭제하면 상태가 DELETED가 된다`() {
        val conversation = Conversation(memberId = 1L)
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val deleted = conversationService.deleteConversation(1L, 10L)

        assertEquals(ConversationStatus.DELETED, deleted.status)
    }

    @Test
    fun `종료된 채팅방도 삭제할 수 있다`() {
        val conversation = Conversation(memberId = 1L).apply { end() }
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val deleted = conversationService.deleteConversation(1L, 10L)

        assertEquals(ConversationStatus.DELETED, deleted.status)
    }

    @Test
    fun `이미 삭제된 채팅방을 다시 삭제하면 CONVERSATION_ALREADY_DELETED`() {
        val conversation = Conversation(memberId = 1L).apply { delete() }
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.deleteConversation(1L, 10L)
            }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
    }

    @Test
    fun `남의 채팅방을 삭제하면 CONVERSATION_ACCESS_DENIED`() {
        val conversation = Conversation(memberId = 2L)
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.deleteConversation(1L, 10L)
            }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `삭제된 채팅방을 종료하면 CONVERSATION_ALREADY_DELETED`() {
        val conversation = Conversation(memberId = 1L).apply { delete() }
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.endConversation(1L, 10L)
            }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
    }

    @Test
    fun `삭제된 채팅방에 메시지를 저장하면 CONVERSATION_ALREADY_DELETED`() {
        val conversation = Conversation(memberId = 1L).apply { delete() }
        every { conversationRepository.findByIdForUpdate(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.saveUserMessage(1L, 10L, "삭제된 방에 쓰기")
            }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
    }

    @Test
    fun `삭제된 채팅방 메시지를 조회하면 CONVERSATION_ALREADY_DELETED`() {
        val conversation = Conversation(memberId = 1L).apply { delete() }
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)

        val exception =
            assertFailsWith<BusinessException> {
                conversationService.getMessages(1L, 10L)
            }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
    }
}
