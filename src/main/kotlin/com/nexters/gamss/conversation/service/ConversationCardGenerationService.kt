package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * **카드 생성이 대화방에 요구하는 것**을 모아 둔다. 상태 선점·전이, 요약 반영, 대상 조회, 그리고
 * 카드를 만들기 위해 대화방에서 읽어야 하는 것들이다.
 *
 * 카드 모듈이 대화방 행을 직접 건드리지 않게 하려고 둔다. 특히 상태 전이 규칙(어느 상태에서 어느
 * 상태로 갈 수 있는가)은 대화 모듈 안에 있어야 한다. 전이는 전부 CAS 다. 카드 생성은 사용자 요청과
 * 새벽 배치가 같은 방을 동시에 노릴 수 있어서, 읽고 나서 쓰는 방식이면 두 경로가 같은 방의 카드를
 * 두 번 만든다.
 */
@Service
class ConversationCardGenerationService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val conversationService: ConversationService,
) {
    /** 대화방 하나. 없으면 null. 전이 실패의 원인(삭제인지 아닌지)을 가릴 때 쓴다. */
    @Transactional(readOnly = true)
    fun findConversation(conversationId: Long): Conversation? = conversationRepository.findById(conversationId).orElse(null)

    /**
     * 이 회원 소유의 대화방. 없으면 CONVERSATION_NOT_FOUND, 남의 방이면 CONVERSATION_ACCESS_DENIED 다.
     *
     * 판정은 [ConversationService.getOwnedConversation] 하나뿐이다. 여기서 다시 쓰면 두 판정이 갈라진다.
     */
    @Transactional(readOnly = true)
    fun getOwnedConversation(
        conversationId: Long,
        memberId: Long,
    ): Conversation = conversationService.getOwnedConversation(conversationId, memberId)

    /** 이 대화방에서 사용자가 보낸 메시지 본문을 시간순으로 읽는다. 감정 분류·요약 폴백의 재료다. */
    @Transactional(readOnly = true)
    fun findUserMessageContents(conversationId: Long): List<String> =
        messageRepository
            .findAllByConversationIdAndSenderTypeOrderByIdAsc(conversationId, SenderType.USER)
            .map { it.content }

    /**
     * 카드 생성을 선점한다(PENDING 으로 전이). 선점에 성공했으면 true, 이미 다른 경로가 잡았거나
     * 방이 삭제됐으면 false.
     *
     * SKIPPED 는 더 이상 새로 저장되지 않지만(#204) 그 값으로 굳은 기존 행은 남아 있다. 빼면 그 방의
     * 카드 생성 요청이 CAS 0건으로 떨어져 엉뚱한 에러가 나가고, 배치도 그 행을 선점하지 못해 영영
     * 카드를 못 받는다.
     */
    @Transactional
    fun claimForCardGeneration(conversationId: Long): Boolean =
        conversationRepository.updateCardGenerationStatus(
            conversationId,
            CardGenerationStatus.PENDING,
            listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED, CardGenerationStatus.SKIPPED),
            Instant.now(),
        ) == 1

    /**
     * 선점(PENDING)한 카드 생성을 [status] 로 끝낸다. 전이했으면 true, 이미 PENDING 이 아니면 false.
     *
     * false 는 방이 삭제됐거나 정리 스케줄러가 먼저 PENDING 을 되돌린 경우다. 부르는 쪽이 그 둘을
     * 구분해 처리한다.
     */
    @Transactional
    fun finishCardGeneration(
        conversationId: Long,
        status: CardGenerationStatus,
    ): Boolean =
        conversationRepository.updateCardGenerationStatus(
            conversationId,
            status,
            listOf(CardGenerationStatus.PENDING),
            Instant.now(),
        ) == 1

    /** 카드로 만든 요약을 대화방에도 남긴다. 다른 채팅방 댓글이 '과거 맥락'으로 읽는 자리다. */
    @Transactional
    fun updateSummary(
        conversationId: Long,
        summary: String,
    ) {
        conversationRepository.updateSummary(conversationId, summary)
    }

    /** [createdAfter, createdBefore) 사이에 만들어진 방 중 자동 카드 생성 대상 id. */
    @Transactional(readOnly = true)
    fun findAutoCardTargetIds(
        createdAfter: Instant,
        createdBefore: Instant,
    ): List<Long> = conversationRepository.findAutoCardTargetIds(createdAfter, createdBefore)
}
