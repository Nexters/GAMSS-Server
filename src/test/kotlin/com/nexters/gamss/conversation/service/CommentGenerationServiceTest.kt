package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.generation.ReplyGenerationOutput
import com.nexters.gamss.llm.parsing.CommentDraft
import com.nexters.gamss.llm.parsing.CommentFeed
import com.nexters.gamss.llm.parsing.CommentFeedValidator
import com.nexters.gamss.llm.parsing.TikitakaDraft
import com.nexters.gamss.llm.selection.CharacterSelector
import com.nexters.gamss.llm.selection.EongttungTopicSelector
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommentGenerationServiceTest {
    private val messageRepository = mockk<MessageRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val characterSelector = mockk<CharacterSelector>()
    private val eongttungTopicSelector = mockk<EongttungTopicSelector>()
    private val commentGenerator = mockk<CommentGenerator>()
    private val commentFeedValidator = mockk<CommentFeedValidator>()
    private val commentPersistenceService = mockk<CommentPersistenceService>()
    private val generationLogRecorder = mockk<GenerationLogRecorder>(relaxed = true)
    private val dailyTokenLimitService = mockk<DailyTokenLimitService> { every { isWithinLimit(any()) } returns true }

    private val service =
        CommentGenerationService(
            messageRepository,
            conversationRepository,
            characterSelector,
            eongttungTopicSelector,
            commentGenerator,
            commentFeedValidator,
            commentPersistenceService,
            generationLogRecorder,
            dailyTokenLimitService,
        )

    private val characters = listOf(EmotionType.JOY, EmotionType.WARM, EmotionType.GRUMPY)
    private val tikitakaCount = 3

    private fun rootMessage(): Message = Message(conversationId = 10L, senderType = SenderType.USER, content = "오늘 억울한 일이 있었어")

    private fun userReplyMessage(commentStatus: CommentStatus = CommentStatus.NONE): Message =
        Message(
            conversationId = 10L,
            senderType = SenderType.USER,
            content = "그건 좀 아니지 않아?",
            repliesToMessageId = 2L,
            commentStatus = commentStatus,
        )

    // id는 characterMessage(2L)/diaryMessage(3L)에 명시적으로 다르게 부여한다 — 둘 다 기본값(0)이면
    // rootMessageId에 잘못된 쪽의 id가 들어가도 테스트가 못 잡아낸다(둘 다 0이라 우연히 통과).
    private fun characterMessage(): Message =
        Message(
            conversationId = 10L,
            senderType = SenderType.CHARACTER,
            emotionType = EmotionType.JOY,
            content = "오늘 진짜 잘했다!",
            rootMessageId = 3L,
        ).also { ReflectionTestUtils.setField(it, "id", 2L) }

    private fun diaryMessage(): Message =
        Message(conversationId = 10L, senderType = SenderType.USER, content = "오늘 있었던 일")
            .also { ReflectionTestUtils.setField(it, "id", 3L) }

    private fun feed(): CommentFeed =
        CommentFeed(
            comments = characters.map { CommentDraft(it, "댓글-$it") },
            tikitaka = listOf(TikitakaDraft(EmotionType.JOY, EmotionType.WARM, "티키타카")),
        )

    private fun stubClaimSuccess() {
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 1
        every { characterSelector.select() } returns characters
        every { characterSelector.selectTikitakaCount() } returns tikitakaCount
        every { conversationRepository.findRandomPastSummaries(any(), any()) } returns emptyList()
    }

    @Test
    fun `선점에 성공하면 LLM을 호출하고 저장한 뒤 DONE을 반환한다`() {
        val message = rootMessage()
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        stubClaimSuccess()
        every {
            commentGenerator.generateComment("", emptyList(), message.content, characters, tikitakaCount, null)
        } returns CommentGenerationOutput(feed(), 123, 0)
        every { commentFeedValidator.validate(feed(), characters, tikitakaCount) } returns Unit
        val savedMessages =
            listOf(Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "댓글"))
        every { commentPersistenceService.saveFeed(10L, 1L, feed()) } returns savedMessages

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(savedMessages, result.messages)
        assertEquals(123, result.usedTokens)
    }

    @Test
    fun `조회된 과거 요약을 그대로 LLM 프롬프트 생성에 전달한다`() {
        val message = rootMessage()
        val pastSummaries = listOf("지난주에 이별 얘기했음", "저번 달에 승진 축하받음")
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 1
        every { characterSelector.select() } returns characters
        every { characterSelector.selectTikitakaCount() } returns tikitakaCount
        every { conversationRepository.findRandomPastSummaries(1L, 10L) } returns pastSummaries
        every {
            commentGenerator.generateComment("", pastSummaries, message.content, characters, tikitakaCount, null)
        } returns CommentGenerationOutput(feed(), 123, 0)
        every { commentFeedValidator.validate(feed(), characters, tikitakaCount) } returns Unit
        every { commentPersistenceService.saveFeed(10L, 1L, feed()) } returns emptyList()

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        verify(exactly = 1) {
            commentGenerator.generateComment("", pastSummaries, message.content, characters, tikitakaCount, null)
        }
    }

    @Test
    fun `선점에 실패하고 현재 상태가 PENDING이면 GENERATING을 반환한다`() {
        val message = rootMessage()
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 0

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.GENERATING, result.outcome)
        assertEquals(null, result.usedTokens)
        verify(exactly = 0) { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `선점에 실패하고 현재 상태가 DONE이면 기존 댓글을 조회해서 DONE을 반환한다`() {
        val doneMessage =
            Message(
                conversationId = 10L,
                senderType = SenderType.USER,
                content = "내용",
                commentStatus = CommentStatus.DONE,
            )
        every { messageRepository.findById(1L) } returnsMany listOf(Optional.of(rootMessage()), Optional.of(doneMessage))
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 0
        val existingComments =
            listOf(Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "이미 생성됨"))
        every { messageRepository.findAllByRootMessageIdOrderByIdAsc(1L) } returns existingComments

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(existingComments, result.messages)
        assertEquals(null, result.usedTokens)
    }

    @Test
    fun `LLM 호출이 재시도까지 실패하면 FAILED로 마킹하고 FAILED를 반환한다`() {
        val message = rootMessage()
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        stubClaimSuccess()
        every { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) } throws
            CommentGenerationFailedException("LLM 호출 실패")
        every { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) } returns 1

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.FAILED, result.outcome)
        assertEquals(null, result.usedTokens)
        verify(exactly = 2) { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) }
    }

    @Test
    fun `예상치 못한 예외가 발생하면 FAILED로 마킹하고 FAILED를 반환한다`() {
        val message = rootMessage()
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        stubClaimSuccess()
        every { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) } returns CommentGenerationOutput(feed(), 123, 0)
        every { commentFeedValidator.validate(feed(), characters, tikitakaCount) } returns Unit
        every { commentPersistenceService.saveFeed(10L, 1L, feed()) } throws RuntimeException("DB 제약조건 위반")
        every { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) } returns 1

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.FAILED, result.outcome)
        assertEquals(null, result.usedTokens)
        verify(exactly = 1) { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) }
    }

    @Test
    fun `BusinessException이 발생하면 FAILED로 전이한 뒤 그대로 다시 던진다`() {
        val message = rootMessage()
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        stubClaimSuccess()
        every { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) } returns CommentGenerationOutput(feed(), 123, 0)
        every { commentFeedValidator.validate(feed(), characters, tikitakaCount) } returns Unit
        every { commentPersistenceService.saveFeed(10L, 1L, feed()) } throws BusinessException(ErrorCode.CONVERSATION_NOT_FOUND)
        every { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) } returns 1

        val exception = assertFailsWith<BusinessException> { service.generateComments(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, exception.errorCode)
        verify(exactly = 1) { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) }
    }

    @Test
    fun `엉뚱이가 선택되면 소재를 골라서 넘긴다`() {
        val message = rootMessage()
        val charactersWithQuirky = listOf(EmotionType.JOY, EmotionType.QUIRKY)
        every { messageRepository.findById(1L) } returns Optional.of(message)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 1
        every { characterSelector.select() } returns charactersWithQuirky
        every { characterSelector.selectTikitakaCount() } returns 1
        every { eongttungTopicSelector.select() } returns "소재"
        every { conversationRepository.findRandomPastSummaries(any(), any()) } returns emptyList()
        val quirkyFeed =
            CommentFeed(
                comments = charactersWithQuirky.map { CommentDraft(it, "댓글-$it") },
                tikitaka = listOf(TikitakaDraft(EmotionType.JOY, EmotionType.QUIRKY, "티키타카")),
            )
        every {
            commentGenerator.generateComment("", emptyList(), message.content, charactersWithQuirky, 1, "소재")
        } returns CommentGenerationOutput(quirkyFeed, 456, 0)
        every { commentFeedValidator.validate(quirkyFeed, charactersWithQuirky, 1) } returns Unit
        every { commentPersistenceService.saveFeed(10L, 1L, quirkyFeed) } returns emptyList()

        val result = service.generateComments(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(456, result.usedTokens)
        verify(exactly = 1) { eongttungTopicSelector.select() }
    }

    @Test
    fun `존재하지 않는 메시지면 MESSAGE_NOT_FOUND`() {
        every { messageRepository.findById(99L) } returns Optional.empty()

        val exception = assertFailsWith<BusinessException> { service.generateComments(memberId = 1L, messageId = 99L) }

        assertEquals(ErrorCode.MESSAGE_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `캐릭터 메시지에 댓글 생성을 요청하면 INVALID_COMMENT_TARGET`() {
        val characterMessage =
            Message(
                conversationId = 10L,
                senderType = SenderType.CHARACTER,
                emotionType = EmotionType.JOY,
                content = "캐릭터 댓글",
            )
        every { messageRepository.findById(1L) } returns Optional.of(characterMessage)

        val exception = assertFailsWith<BusinessException> { service.generateComments(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.INVALID_COMMENT_TARGET, exception.errorCode)
    }

    @Test
    fun `남의 채팅방 메시지면 CONVERSATION_ACCESS_DENIED`() {
        every { messageRepository.findById(1L) } returns Optional.of(rootMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 2L))

        val exception = assertFailsWith<BusinessException> { service.generateComments(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.CONVERSATION_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `삭제된 채팅방의 메시지면 댓글 생성 없이 CONVERSATION_ALREADY_DELETED`() {
        every { messageRepository.findById(1L) } returns Optional.of(rootMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L).apply { delete() })

        val exception = assertFailsWith<BusinessException> { service.generateComments(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
        verify(exactly = 0) { messageRepository.updateCommentStatus(any(), any(), any(), any()) }
        verify(exactly = 0) { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `답글 선점에 성공하면 LLM을 호출하고 저장한 뒤 DONE을 반환한다`() {
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 1
        every {
            commentGenerator.generateReply(diaryMessage().content, "gippeum", characterMessage().content, userReplyMessage().content)
        } returns ReplyGenerationOutput("그치! 잘했어!", 77, 0)
        every { commentFeedValidator.validateReply("그치! 잘했어!") } returns Unit
        val savedReply =
            Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "그치! 잘했어!")
        every { commentPersistenceService.saveReply(10L, 3L, 1L, EmotionType.JOY, "그치! 잘했어!") } returns savedReply

        val result = service.generateReplyComment(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(listOf(savedReply), result.messages)
        assertEquals(77, result.usedTokens)
    }

    @Test
    fun `답글 선점에 실패하고 현재 상태가 PENDING이면 GENERATING을 반환한다`() {
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 0

        val result = service.generateReplyComment(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.GENERATING, result.outcome)
        verify(exactly = 0) { commentGenerator.generateReply(any(), any(), any(), any()) }
    }

    @Test
    fun `답글 선점에 실패하고 현재 상태가 DONE이면 기존 답글을 조회해서 DONE을 반환한다`() {
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage(commentStatus = CommentStatus.DONE))
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 0
        val existingReply =
            Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "이미 생성됨")
        every { messageRepository.findByRepliesToMessageId(1L) } returns existingReply

        val result = service.generateReplyComment(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(listOf(existingReply), result.messages)
    }

    @Test
    fun `답글 선점에 실패했는데 상태는 DONE이고 답글을 찾을 수 없으면 FAILED를 반환한다`() {
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage(commentStatus = CommentStatus.DONE))
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 0
        every { messageRepository.findByRepliesToMessageId(1L) } returns null

        val result = service.generateReplyComment(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.FAILED, result.outcome)
        assertEquals(emptyList<Message>(), result.messages)
    }

    @Test
    fun `답글 LLM 호출이 재시도까지 실패하면 FAILED로 마킹하고 FAILED를 반환한다`() {
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 1
        every { commentGenerator.generateReply(any(), any(), any(), any()) } throws CommentGenerationFailedException("LLM 호출 실패")
        every { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) } returns 1

        val result = service.generateReplyComment(memberId = 1L, messageId = 1L)

        assertEquals(CommentGenerationOutcome.FAILED, result.outcome)
        verify(exactly = 2) { commentGenerator.generateReply(any(), any(), any(), any()) }
        verify(exactly = 1) { messageRepository.updateCommentStatus(1L, CommentStatus.FAILED, listOf(CommentStatus.PENDING), any()) }
    }

    @Test
    fun `답글 대상이 캐릭터 메시지가 아니면 INVALID_COMMENT_TARGET`() {
        val nonCharacterTarget = Message(conversationId = 10L, senderType = SenderType.USER, content = "다른 유저 메시지")
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every { messageRepository.findById(2L) } returns Optional.of(nonCharacterTarget)

        val exception = assertFailsWith<BusinessException> { service.generateReplyComment(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.INVALID_COMMENT_TARGET, exception.errorCode)
    }

    @Test
    fun `답글 대상이 다른 채팅방의 메시지면 INVALID_COMMENT_TARGET`() {
        val characterMessageInOtherConversation =
            Message(
                conversationId = 20L,
                senderType = SenderType.CHARACTER,
                emotionType = EmotionType.JOY,
                content = "다른 채팅방의 댓글",
                rootMessageId = 3L,
            )
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every { messageRepository.findById(2L) } returns Optional.of(characterMessageInOtherConversation)

        val exception = assertFailsWith<BusinessException> { service.generateReplyComment(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.INVALID_COMMENT_TARGET, exception.errorCode)
        verify(exactly = 0) { messageRepository.findById(3L) }
        verify(exactly = 0) { commentGenerator.generateReply(any(), any(), any(), any()) }
    }

    @Test
    fun `답글의 원본 일기가 다른 채팅방의 메시지면 INVALID_COMMENT_TARGET`() {
        val diaryMessageInOtherConversation =
            Message(conversationId = 20L, senderType = SenderType.USER, content = "다른 채팅방의 일기")
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessageInOtherConversation)

        val exception = assertFailsWith<BusinessException> { service.generateReplyComment(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.INVALID_COMMENT_TARGET, exception.errorCode)
        verify(exactly = 0) { messageRepository.updateCommentStatus(any(), any(), any(), any()) }
        verify(exactly = 0) { commentGenerator.generateReply(any(), any(), any(), any()) }
    }

    @Test
    fun `답글이 아닌 메시지로 답글 생성을 요청하면 INVALID_COMMENT_TARGET`() {
        val notAReply = Message(conversationId = 10L, senderType = SenderType.USER, content = "그냥 메시지", repliesToMessageId = null)
        every { messageRepository.findById(1L) } returns Optional.of(notAReply)
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L))

        val exception = assertFailsWith<BusinessException> { service.generateReplyComment(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.INVALID_COMMENT_TARGET, exception.errorCode)
    }

    @Test
    fun `삭제된 채팅방의 답글이면 재응답 생성 없이 CONVERSATION_ALREADY_DELETED`() {
        every { messageRepository.findById(1L) } returns Optional.of(userReplyMessage())
        every { conversationRepository.findById(10L) } returns Optional.of(Conversation(memberId = 1L).apply { delete() })

        val exception = assertFailsWith<BusinessException> { service.generateReplyComment(memberId = 1L, messageId = 1L) }

        assertEquals(ErrorCode.CONVERSATION_ALREADY_DELETED, exception.errorCode)
        verify(exactly = 0) { messageRepository.updateCommentStatus(any(), any(), any(), any()) }
        verify(exactly = 0) { commentGenerator.generateReply(any(), any(), any(), any()) }
    }

    // generateFor는 저장 직후 호출되는 진입점이라 소유권 조회 없이 message 자체로 라우팅한다
    // (CommentGenerationService.getOwnedRootMessage를 타지 않음). 선점·재시도·저장 로직 자체는
    // generateComments/generateReplyComment와 같은 내부 메서드를 공유하므로, 여기서는 라우팅이
    // 올바른 쪽으로 가는지만 확인하고 나머지 경로(FAILED, 재시도 등)는 위 테스트들이 이미 커버한다.

    @Test
    fun `repliesToMessageId가 없으면 generateFor가 댓글 생성으로 라우팅한다`() {
        val message = rootMessage().also { ReflectionTestUtils.setField(it, "id", 1L) }
        stubClaimSuccess()
        every {
            commentGenerator.generateComment(null, emptyList(), message.content, characters, tikitakaCount, null)
        } returns CommentGenerationOutput(feed(), 123, 0)
        every { commentFeedValidator.validate(feed(), characters, tikitakaCount) } returns Unit
        val savedMessages =
            listOf(Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "댓글"))
        every { commentPersistenceService.saveFeed(10L, 1L, feed()) } returns savedMessages

        val result = service.generateFor(1L, message, null)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(savedMessages, result.messages)
        assertEquals(123, result.usedTokens)
        verify(exactly = 0) { commentGenerator.generateReply(any(), any(), any(), any()) }
    }

    @Test
    fun `generateFor가 받은 currentConversationSummary를 그대로 LLM 호출에 전달한다`() {
        val message = rootMessage().also { ReflectionTestUtils.setField(it, "id", 1L) }
        stubClaimSuccess()
        every {
            commentGenerator.generateComment("현재 요약", emptyList(), message.content, characters, tikitakaCount, null)
        } returns CommentGenerationOutput(feed(), 123, 0)
        every { commentFeedValidator.validate(feed(), characters, tikitakaCount) } returns Unit
        every { commentPersistenceService.saveFeed(10L, 1L, feed()) } returns emptyList()

        val result = service.generateFor(1L, message, "현재 요약")

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        verify(exactly = 1) {
            commentGenerator.generateComment("현재 요약", emptyList(), message.content, characters, tikitakaCount, null)
        }
    }

    @Test
    fun `repliesToMessageId가 있으면 generateFor가 재응답 생성으로 라우팅한다`() {
        val reply = userReplyMessage().also { ReflectionTestUtils.setField(it, "id", 1L) }
        every { messageRepository.findById(2L) } returns Optional.of(characterMessage())
        every { messageRepository.findById(3L) } returns Optional.of(diaryMessage())
        every {
            messageRepository.updateCommentStatus(1L, CommentStatus.PENDING, listOf(CommentStatus.NONE, CommentStatus.FAILED), any())
        } returns 1
        every {
            commentGenerator.generateReply(diaryMessage().content, "gippeum", characterMessage().content, reply.content)
        } returns ReplyGenerationOutput("그치! 잘했어!", 77, 0)
        every { commentFeedValidator.validateReply("그치! 잘했어!") } returns Unit
        val savedReply =
            Message(conversationId = 10L, senderType = SenderType.CHARACTER, emotionType = EmotionType.JOY, content = "그치! 잘했어!")
        every { commentPersistenceService.saveReply(10L, 3L, 1L, EmotionType.JOY, "그치! 잘했어!") } returns savedReply

        val result = service.generateFor(1L, reply, null)

        assertEquals(CommentGenerationOutcome.DONE, result.outcome)
        assertEquals(listOf(savedReply), result.messages)
        assertEquals(77, result.usedTokens)
        verify(exactly = 0) { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `일일 토큰 상한을 넘으면 저장은 유지한 채 생성을 건너뛰고 LIMIT_EXCEEDED를 반환한다`() {
        val message = rootMessage().also { ReflectionTestUtils.setField(it, "id", 1L) }
        every { dailyTokenLimitService.isWithinLimit(1L) } returns false

        val result = service.generateFor(1L, message, null)

        assertEquals(CommentGenerationOutcome.LIMIT_EXCEEDED, result.outcome)
        // 생성도, 선점(CAS)도 하지 않는다 — 저장(컨트롤러가 이미 함)만 남고 토큰 소비는 없다.
        verify(exactly = 0) { commentGenerator.generateComment(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { messageRepository.updateCommentStatus(any(), any(), any(), any()) }
    }
}
