package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.config.ConversationProperties
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대화·메시지가 **얼마나 있는지** 센다. 백오피스 대시보드 집계와 모니터링 게이지가 쓴다.
 *
 * 사용자 유스케이스([ConversationService])와 갈라 둔 이유는 바뀌는 이유가 다르기 때문이다.
 * 여기 있는 것들은 "무엇을 보고 싶은가"가 바뀔 때 함께 바뀌고, 대화 자체의 규칙과는 무관하다.
 *
 * 다른 모듈이 [ConversationRepository]·[MessageRepository] 를 직접 잡지 않게 하는 역할도 겸한다.
 * 리포지토리를 직접 열어주면 쿼리 하나를 고칠 때 어느 모듈이 깨지는지 알 수 없다.
 */
@Service
@Transactional(readOnly = true)
class ConversationStatsService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val conversationProperties: ConversationProperties,
) {
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

    /** 대화방을 [pageable] 대로 페이지네이션한다(백오피스 대화방별 사용량 목록). */
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
}
