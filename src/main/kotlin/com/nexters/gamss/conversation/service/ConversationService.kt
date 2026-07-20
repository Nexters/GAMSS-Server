package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Service
class ConversationService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val properties: ConversationProperties,
) {
    /** 사용자 메시지를 저장한다. conversationId가 없으면 새 채팅방을 만들어 담는다. */
    @Transactional
    fun saveUserMessage(
        memberId: Long,
        conversationId: Long?,
        content: String,
        repliesToMessageId: Long? = null,
    ): Message {
        if (conversationId == null && repliesToMessageId != null) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "새 채팅방을 만들면서 답장할 수 없습니다.")
        }
        val conversation =
            conversationId?.let { getOwnedConversation(it, memberId) }
                ?: conversationRepository.save(Conversation(memberId))
        if (repliesToMessageId != null) {
            validateReplyTarget(repliesToMessageId, conversation.id)
        }
        val message =
            conversation.createMessage(
                senderType = SenderType.USER,
                content = content,
                emotionType = null,
                repliesToMessageId = repliesToMessageId,
            )
        return messageRepository.save(message)
    }

    /** 답장 대상 메시지가 실제로 해당 채팅방에 존재하는지 확인한다. */
    private fun validateReplyTarget(
        repliesToMessageId: Long,
        conversationId: Long,
    ) {
        val target =
            messageRepository
                .findById(repliesToMessageId)
                .orElseThrow { BusinessException(ErrorCode.INVALID_INPUT, "답장 대상 메시지를 찾을 수 없습니다.") }
        if (target.conversationId != conversationId) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "답장 대상 메시지가 해당 채팅방에 없습니다.")
        }
    }

    /** 서비스상 하루(dayStartTime ~ 익일 dayStartTime, KST) 동안 생성된 채팅방 목록을 조회한다. */
    @Transactional(readOnly = true)
    fun getConversations(
        memberId: Long,
        date: LocalDate,
    ): List<Conversation> {
        val start = date.atTime(properties.dayStartTime).atZone(ZONE).toInstant()
        val end =
            date
                .plusDays(1)
                .atTime(properties.dayStartTime)
                .atZone(ZONE)
                .toInstant()
        return conversationRepository.findAllByMemberIdAndCreatedAtInRange(memberId, start, end)
    }

    @Transactional(readOnly = true)
    fun getMessages(
        memberId: Long,
        conversationId: Long,
    ): List<Message> {
        getOwnedConversation(conversationId, memberId)
        return messageRepository.findAllByConversationIdOrderByIdAsc(conversationId)
    }

    private fun getOwnedConversation(
        conversationId: Long,
        memberId: Long,
    ): Conversation {
        val conversation =
            conversationRepository
                .findById(conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        if (!conversation.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED)
        }
        return conversation
    }

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
    }
}
