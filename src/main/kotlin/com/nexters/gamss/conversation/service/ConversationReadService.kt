package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대화·메시지를 **모듈 밖에서** 읽어가는 창구(백오피스 집계·모니터링 게이지·미종료 리마인더).
 *
 * 이 창구를 두는 이유는 다른 모듈이 [ConversationRepository]·[MessageRepository] 를 직접 잡지 않게
 * 하기 위해서다. 리포지토리를 직접 열어주면 쿼리 하나를 고칠 때 어느 모듈이 깨지는지 알 수 없고,
 * 대화 모듈이 자기 저장 구조를 바꿀 자유를 잃는다.
 *
 * **읽기만 둔다.** 상태를 바꾸는 일은 [ConversationService] 처럼 그 일을 책임지는 서비스가 맡는다.
 * 모듈 안에서 쓰는 조회까지 여기로 모으지 않는다. 여기 있는 것은 밖에서 요청한 것뿐이다.
 */
@Service
@Transactional(readOnly = true)
class ConversationReadService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val conversationProperties: ConversationProperties,
) {
    /** 대화방 하나. 없으면 null. */
    fun findConversation(conversationId: Long): Conversation? = conversationRepository.findById(conversationId).orElse(null)

    /**
     * 이 회원 소유의 대화방. 없으면 CONVERSATION_NOT_FOUND, 남의 방이면 CONVERSATION_ACCESS_DENIED 다.
     *
     * 소유 판정을 부르는 쪽마다 다시 쓰지 않도록 대화 모듈이 한 번만 정의한다.
     */
    fun getOwnedConversation(
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

    /** 이 대화방에서 사용자가 보낸 메시지 본문을 시간순으로 읽는다. */
    fun findUserMessageContents(conversationId: Long): List<String> =
        messageRepository
            .findAllByConversationIdAndSenderTypeOrderByIdAsc(conversationId, SenderType.USER)
            .map { it.content }

    /** [from, to) 사이 생성된 대화방 수. */
    fun countConversationsCreatedBetween(
        from: Instant,
        to: Instant,
    ): Long = conversationRepository.countCreatedBetween(from, to)

    /** [from] 이후 생성된 대화방의 생성시각. 일별 추이는 받는 쪽이 묶는다. */
    fun findConversationCreatedAtsSince(from: Instant): List<Instant> = conversationRepository.findCreatedAtsSince(from)

    /** [from, to) 사이 사용자가 보낸 메시지 수. */
    fun countUserMessagesCreatedBetween(
        from: Instant,
        to: Instant,
    ): Long = messageRepository.countBySenderTypeCreatedBetween(SenderType.USER, from, to)

    /** [from, to) 사이 메시지를 남긴 회원 수(중복 제외). */
    fun countActiveMembersBetween(
        from: Instant,
        to: Instant,
    ): Long = messageRepository.countActiveMembersBetween(SenderType.USER, from, to)

    /** [from] 이후 작성된 메시지의 작성시각. 일별 추이는 받는 쪽이 묶는다. */
    fun findMessageCreatedAtsSince(from: Instant): List<Instant> = messageRepository.findCreatedAtsSince(from)

    /** 대화방을 [pageable] 대로 페이지네이션한다. */
    fun findConversations(pageable: Pageable): Page<Conversation> = conversationRepository.findAll(pageable)

    /** [conversationIds] 각각의 발신 주체별 메시지 수. 대화방 수만큼 쿼리하지 않도록 한 번에 받는다. */
    fun countMessagesBySender(conversationIds: Collection<Long>): List<ConversationSenderCount> =
        messageRepository.countBySenderForConversations(conversationIds).map {
            ConversationSenderCount(conversationId = it.conversationId, senderType = it.senderType, count = it.count)
        }

    /** [status] 상태의 대화방 수(모니터링 게이지). */
    fun countConversationsByStatus(status: ConversationStatus): Long = conversationRepository.countByStatus(status)

    /** 카드 생성이 [status] 로 남아 있는 대화방 수(모니터링 게이지). */
    fun countConversationsByCardGenerationStatus(status: CardGenerationStatus): Long =
        conversationRepository.countByCardGenerationStatus(status)

    /** [commentStatus] 상태의 메시지 수(모니터링 게이지). */
    fun countMessagesByCommentStatus(commentStatus: CommentStatus): Long = messageRepository.countByCommentStatus(commentStatus)

    /**
     * 고아로 볼 만큼 오래 PENDING 으로 멈춰 있는 댓글 생성 수. 즉시 대응이 필요한 적체 신호다.
     *
     * '얼마나 오래'의 기준([ConversationProperties.pendingGenerationTimeout])은 대화 모듈이 정한다.
     * 부르는 쪽이 그 값을 읽어 시각을 계산해 넘기면, 정리 스케줄러([PendingCommentCleanupScheduler])가
     * 쓰는 기준과 조용히 어긋날 수 있다.
     */
    fun countStuckPendingComments(): Long =
        messageRepository.countByCommentStatusOlderThan(
            CommentStatus.PENDING,
            Instant.now().minus(conversationProperties.pendingGenerationTimeout),
        )

    /** [createdAfter, createdBefore) 사이에 만들어진 방 중 아직 미종료인 것들의 주인 회원 id(중복 제외). */
    fun findMemberIdsWithUnfinishedConversations(
        createdAfter: Instant,
        createdBefore: Instant,
    ): List<Long> = conversationRepository.findMemberIdsWithUnfinishedConversations(createdAfter, createdBefore)
}
