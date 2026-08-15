package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.domain.ConversationTitle
import com.nexters.gamss.conversation.domain.ExcludedEmotionTypes
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
class ConversationService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val cleaners: DeletedConversationCleaners,
) {
    /**
     * 사용자 메시지를 저장한다. conversationId가 없으면 새 채팅방을 만들어 담는다.
     *
     * [excludeCharacters]는 새 채팅방을 만들 때만 반영된다 — 기존 채팅방(conversationId 있음)에
     * 이어서 보내는 요청에 함께 와도 조용히 무시하고 최초 설정을 그대로 둔다.
     */
    @Transactional
    fun saveUserMessage(
        memberId: Long,
        conversationId: Long?,
        content: String,
        repliesToMessageId: Long? = null,
        excludeCharacters: List<EmotionType>? = null,
    ): Message {
        if (conversationId == null && repliesToMessageId != null) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "새 채팅방을 만들면서 답장할 수 없습니다.")
        }
        val conversation =
            conversationId?.let { getOwnedConversationForUpdate(it, memberId) }
                ?: conversationRepository.save(Conversation(memberId, resolveExcludedEmotionTypes(excludeCharacters)))
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

    /**
     * 채팅방을 삭제한다. 삭제 후에는 채팅방 목록·메시지 조회에 나타나지 않는다.
     *
     * 이 방에 딸린 자원(카드 등)도 같은 시각으로 함께 정리한다([DeletedConversationCleaner]) —
     * 방이 사라졌는데 카드만 살아 있으면 반대 방향(카드를 지우면 방까지 지운다)과 데이터가 어긋난다.
     *
     * **정리를 채팅방 잠금보다 먼저 한다.** 카드 단건 삭제
     * ([com.nexters.gamss.card.service.CardService.deleteCard])가 카드 → 채팅방 순으로 행을 잡으므로,
     * 여기서 채팅방을 먼저 잠그면 동시에 들어온 두 요청이 서로가 잡은 행을 기다리다 데드락으로 죽는다.
     * 그래서 소유권·삭제 여부는 잠그지 않는 읽기로 먼저 확인하고, 상태 전이는 잠금을 잡은 뒤
     * [Conversation.delete]가 다시 판정한다 — 그 사이 다른 요청이 삭제를 끝냈다면 여기서
     * CONVERSATION_ALREADY_DELETED로 실패하고 앞서 지운 자원도 함께 롤백된다.
     */
    @Transactional
    fun deleteConversation(
        memberId: Long,
        conversationId: Long,
    ): Conversation {
        getOwnedConversation(conversationId, memberId).ensureNotDeleted()
        cleaners.cleanAll(listOf(conversationId), Instant.now())
        val conversation = getOwnedConversationForUpdate(conversationId, memberId)
        conversation.delete()
        return conversation
    }

    /**
     * 채팅방 여러 개를 한 번에 삭제하고 실제로 삭제된 개수를 돌려준다.
     *
     * 본인 방만 지운다 — 남의 방·없는 방·이미 지운 방 id가 섞여 있어도 그것만 빠지고 나머지는
     * 정상 삭제된다(카드 일괄 삭제와 같은 계약). 단건 삭제처럼 403·404·409로 전체를 거절하면 id
     * 하나 때문에 나머지를 못 지우고, 연속 호출도 안전하지 않다.
     *
     * 행 잠금을 쓰지 않는다 — 삭제는 `status <> DELETED` 조건부 UPDATE라 동시에 들어온 요청이
     * 같은 방을 두 번 세지 않는다.
     *
     * **딸린 자원을 먼저 정리하고 채팅방을 지운다.** 카드 일괄 삭제
     * ([com.nexters.gamss.card.service.CardService.deleteCardsWithConversations])가 카드 → 채팅방
     * 순으로 행을 잡으므로, 반대로 잡으면 동시에 들어온 두 요청이 데드락으로 죽는다. 대상은
     * [ConversationRepository.findDeletableIds]로 이미 확정해둔 뒤라 순서를 바꿔도 지워지는 집합은
     * 같다.
     */
    @Transactional
    fun deleteConversations(
        memberId: Long,
        conversationIds: List<Long>,
    ): Int {
        val deletableIds = conversationRepository.findDeletableIds(memberId, conversationIds.distinct())
        if (deletableIds.isEmpty()) {
            return 0
        }
        // 채팅방과 딸린 자원에 같은 시각을 찍는다 — 한 번의 삭제로 사라진 것들이 이력에서 서로 다른
        // 요청처럼 보이지 않아야 한다(카드 일괄 삭제도 같은 이유로 시각을 공유한다).
        val now = Instant.now()
        cleaners.cleanAll(deletableIds, now)
        return conversationRepository.softDeleteByIds(deletableIds, now)
    }

    /**
     * 채팅방 제목을 지정·변경한다. 여러 번 호출할 수 있다. 삭제된 방은 변경할 수 없다.
     * 제목도 상태를 바꾸는 요청이라 행 잠금([getOwnedConversationForUpdate])을 쓴다 — 잠금 없이
     * stale 상태로 읽으면 동시 삭제가 flush 로 되살아나거나(모든 컬럼 UPDATE) 삭제된 방의 제목이 바뀔 수 있다.
     */
    @Transactional
    fun updateTitle(
        memberId: Long,
        conversationId: Long,
        title: String,
    ): Conversation {
        val conversation = getOwnedConversationForUpdate(conversationId, memberId)
        conversation.rename(ConversationTitle(title))
        return conversation
    }

    /**
     * 제외 요청 캐릭터 목록을 검증한다(중복 제거·최대 개수는 [ExcludedEmotionTypes]가 담당). 전체를
     * 다 제외하면 [com.nexters.gamss.llm.selection.CharacterSelector]가 뽑을 캐릭터가 하나도 남지
     * 않으므로 거부한다.
     */
    private fun resolveExcludedEmotionTypes(excludeCharacters: List<EmotionType>?): ExcludedEmotionTypes =
        if (excludeCharacters.isNullOrEmpty()) ExcludedEmotionTypes.EMPTY else ExcludedEmotionTypes.of(excludeCharacters)

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
        // 캐릭터 메시지가 아닌 대상으로 답장을 저장해버리면, 저장 직후 이어지는 재응답 생성이
        // INVALID_COMMENT_TARGET으로 실패해도 저장 자체는 이미 커밋되어 되돌릴 수 없다 — 저장 시점에
        // 미리 막아 "응답은 에러인데 실제로는 저장된" 상태가 생기지 않게 한다.
        if (target.senderType != SenderType.CHARACTER) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "캐릭터 댓글에만 답장할 수 있습니다.")
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

    /**
     * 아직 진행 중인(쓰다 만) 대화방을 최신순으로 조회한다. 날짜를 몰라도 이어쓸 방을 찾게 하는
     * 목록이라 날짜 조건을 걸지 않는다.
     *
     * **'미완성' 을 [ConversationStatus.ACTIVE] 로 정의하는 판단이 여기에 있다.** 요구사항은
     * "삭제되지 않았고 카드도 생성되지 않은 방"이지만 ACTIVE 하나로 둘 다 충족된다 — 상태 전이가
     * `ACTIVE → ENDED → DELETED` 단방향이고([Conversation.end]·[Conversation.delete] 외에 status 를
     * 바꾸는 코드가 없다), 카드 생성은 ENDED 를 요구하므로([com.nexters.gamss.card.service.CardService]
     * 의 claimForGeneration) **ACTIVE 방은 카드를 가질 수 없다.**
     *
     * 그래서 cards 를 조인하지 않는다 — 걸러질 행이 없고, 읽는 사람에게 'ACTIVE 인데 카드가 있을 수
     * 있다'는 잘못된 인상만 준다. 종료한 방을 다시 열 수 있게 되면 이 전제가 깨지므로, 그때는 카드
     * 존재 여부를 함께 봐야 한다.
     */
    @Transactional(readOnly = true)
    fun getInProgressConversations(memberId: Long): List<Conversation> =
        conversationRepository.findAllByMemberIdAndStatus(memberId, ConversationStatus.ACTIVE)

    @Transactional(readOnly = true)
    fun getMessages(
        memberId: Long,
        conversationId: Long,
    ): List<Message> {
        val conversation = getOwnedConversation(conversationId, memberId)
        conversation.ensureNotDeleted()
        return MessageThreadOrder.reorderTikitakaAfterTarget(messageRepository.findAllByConversationIdOrderByIdAsc(conversationId))
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
