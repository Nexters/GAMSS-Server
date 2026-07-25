package com.nexters.gamss.conversation.service

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
            conversationId?.let { getOwnedConversationForUpdate(it, memberId) }
                ?: conversationRepository.save(Conversation(memberId))
        conversation.ensureActive()
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

    /** 채팅방을 종료한다. 종료 후에는 사용자 메시지를 추가할 수 없다. */
    @Transactional
    fun endConversation(
        memberId: Long,
        conversationId: Long,
    ): Conversation {
        val conversation = getOwnedConversationForUpdate(conversationId, memberId)
        conversation.end()
        return conversation
    }

    /** 채팅방을 삭제한다. 삭제 후에는 채팅방 목록·메시지 조회에 나타나지 않는다. */
    @Transactional
    fun deleteConversation(
        memberId: Long,
        conversationId: Long,
    ): Conversation {
        val conversation = getOwnedConversationForUpdate(conversationId, memberId)
        conversation.delete()
        return conversation
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

    /** 날짜(KST 달력 자정~자정)에 생성된 채팅방 목록을 조회한다. */
    @Transactional(readOnly = true)
    fun getConversations(
        memberId: Long,
        date: LocalDate,
    ): List<Conversation> {
        val start = date.atStartOfDay(ZONE).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZONE).toInstant()
        return conversationRepository.findAllByMemberIdAndCreatedAtInRange(memberId, start, end)
    }

    @Transactional(readOnly = true)
    fun getMessages(
        memberId: Long,
        conversationId: Long,
    ): List<Message> {
        val conversation = getOwnedConversation(conversationId, memberId)
        conversation.ensureNotDeleted()
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

    /**
     * 상태를 바꾸는 요청(메시지 저장·종료·삭제)에서 쓴다. 행 잠금을 걸어, 동시에 들어온 다른 상태
     * 변경 요청이 이 트랜잭션이 끝날 때까지 대기하게 만든다 — 그래야 삭제 이후 작업 차단 계약이
     * "ACTIVE로 읽고 나서 뒤늦게 삭제가 커밋되는" 경합으로 깨지지 않는다.
     */
    private fun getOwnedConversationForUpdate(
        conversationId: Long,
        memberId: Long,
    ): Conversation {
        val conversation =
            conversationRepository
                .findByIdForUpdate(conversationId)
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
