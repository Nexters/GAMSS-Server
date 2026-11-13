package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.ResponseGeneration
import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.conversation.repository.ResponseGenerationRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 캐릭터 응답을 그 응답을 만든 생성 1회(트리거 메시지) 단위로 묶어 돌려준다. 품질 판정과 백오피스 대화 뷰어가 쓴다. */
@Service
@Transactional(readOnly = true)
class ResponseThreadService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val responseGenerationRepository: ResponseGenerationRepository,
) {
    fun thread(conversationId: Long): ConversationThread {
        val conversation =
            conversationRepository.findById(conversationId).orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        val messages = messageRepository.findAllByConversationIdOrderByIdAsc(conversationId)
        val refs = classify(messages.filter { it.senderType == SenderType.CHARACTER }, messages.associateBy { it.id })
        return ConversationThread(conversation, messages, refs, generations(refs.values.map { it.triggerMessageId }))
    }

    fun responses(messageIds: Collection<Long>): List<ResponseDetail> {
        val messages = messageRepository.findAllById(messageIds).filter { it.senderType == SenderType.CHARACTER }
        if (messages.isEmpty()) {
            return emptyList()
        }
        val refs = classify(messages)
        val conversations = conversationRepository.findAllById(messages.map { it.conversationId }.toSet()).associateBy { it.id }
        val generations = generations(refs.values.map { it.triggerMessageId })
        return messages.mapNotNull { message ->
            val ref = refs[message.id] ?: return@mapNotNull null
            ResponseDetail(ref, conversations.getValue(message.conversationId), generations[ref.triggerMessageId])
        }
    }

    fun responsesCreatedBetween(
        from: Instant,
        to: Instant,
    ): List<ResponseRef> = classify(messageRepository.findLiveResponsesCreatedBetween(from, to)).values.toList()

    fun generationUnit(triggerMessageId: Long): GenerationUnit? {
        val trigger = messageRepository.findById(triggerMessageId).orElse(null) ?: return null
        if (trigger.senderType != SenderType.USER) {
            return null
        }
        val conversation = conversationRepository.findById(trigger.conversationId).orElse(null) ?: return null
        val candidates =
            if (trigger.repliesToMessageId == null) {
                messageRepository.findAllByRootMessageIdOrderByIdAsc(trigger.id)
            } else {
                listOfNotNull(messageRepository.findByRepliesToMessageId(trigger.id))
            }
        val responses =
            classify(candidates)
                .values
                .filter { it.triggerMessageId == trigger.id }
                .map { it.message }
                .sortedBy { it.id }
        if (responses.isEmpty()) {
            return null
        }
        val prior =
            messageRepository
                .findByConversationIdAndIdLessThanOrderByIdDesc(conversation.id, trigger.id, PageRequest.of(0, PRIOR_LIMIT))
                .reversed()
        val repliedCharacter = trigger.repliesToMessageId?.let { messageRepository.findById(it).orElse(null) }
        val next =
            messageRepository.findFirstByConversationIdAndSenderTypeAndIdGreaterThanOrderByIdAsc(
                conversation.id,
                SenderType.USER,
                responses.maxOf { it.id },
            )
        return GenerationUnit(
            conversation = conversation,
            trigger = trigger,
            kind = if (trigger.repliesToMessageId == null) ResponseKind.COMMENT else ResponseKind.REPLY,
            responses = MessageThreadOrder.reorderTikitakaAfterTarget(responses),
            prior = prior,
            repliedCharacterMessage = repliedCharacter,
            nextUserMessage = next,
            diary =
                trigger.repliesToMessageId?.let { repliedCharacter?.rootMessageId }?.let {
                    messageRepository
                        .findById(
                            it,
                        ).orElse(null)
                },
        )
    }

    fun generations(triggerMessageIds: Collection<Long>): Map<Long, ResponseGeneration> =
        if (triggerMessageIds.isEmpty()) {
            emptyMap()
        } else {
            responseGenerationRepository.findByTriggerMessageIdIn(triggerMessageIds.toSet()).associateBy { it.triggerMessageId }
        }

    // 답장 대상이 유저 메시지면 답글 재응답이고, 아니면(첫 댓글, 티키타카) 일기에 달린 댓글 묶음이다.
    private fun classify(
        responses: List<Message>,
        known: Map<Long, Message> = emptyMap(),
    ): Map<Long, ResponseRef> {
        val missing = responses.mapNotNull { it.repliesToMessageId }.filter { it !in known }.toSet()
        val targets = known + messageRepository.findAllById(missing).associateBy { it.id }
        return responses
            .mapNotNull { message ->
                val target = message.repliesToMessageId?.let { targets[it] }
                val root = message.rootMessageId
                when {
                    target?.senderType == SenderType.USER -> ResponseRef(message, ResponseKind.REPLY, target.id)
                    root != null -> ResponseRef(message, ResponseKind.COMMENT, root)
                    else -> null
                }
            }.associateBy { it.message.id }
    }

    companion object {
        private const val PRIOR_LIMIT = 20
    }
}

data class ResponseRef(
    val message: Message,
    val kind: ResponseKind,
    val triggerMessageId: Long,
)

data class ResponseDetail(
    val ref: ResponseRef,
    val conversation: Conversation,
    val generation: ResponseGeneration?,
)

data class ConversationThread(
    val conversation: Conversation,
    val messages: List<Message>,
    val responses: Map<Long, ResponseRef>,
    val generations: Map<Long, ResponseGeneration>,
)

data class GenerationUnit(
    val conversation: Conversation,
    val trigger: Message,
    val kind: ResponseKind,
    val responses: List<Message>,
    val prior: List<Message>,
    val repliedCharacterMessage: Message?,
    val nextUserMessage: Message?,
    val diary: Message?,
)
