package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대화방에 붙어 있는 **카드 생성 상태**를 다룬다. 카드 모듈이 대화방 행을 직접 UPDATE 하지 않도록,
 * 상태 전이 규칙(어느 상태에서 어느 상태로 갈 수 있는가)을 대화 모듈 안에 둔다.
 *
 * 전이는 전부 CAS 다. 카드 생성은 사용자 요청과 새벽 배치가 같은 방을 동시에 노릴 수 있어서,
 * 읽고 나서 쓰는 방식이면 두 경로가 같은 방의 카드를 두 번 만든다.
 */
@Service
class ConversationCardStateService(
    private val conversationRepository: ConversationRepository,
) {
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

    /**
     * 카드와 함께 사라지는 대화방들을 일괄 소프트 삭제한다.
     *
     * [deletedAt] 을 받는 이유는 카드와 **같은 시각**을 찍기 위해서다. 한 번의 삭제로 사라진 짝이라
     * 나중에 이력을 볼 때 두 UPDATE 사이의 미세한 시차로 다른 요청처럼 보이지 않아야 한다.
     */
    @Transactional
    fun softDeleteAll(
        conversationIds: List<Long>,
        deletedAt: Instant,
    ) {
        conversationRepository.softDeleteByIds(conversationIds, deletedAt)
    }

    /**
     * 카드가 지워질 때 그 카드가 나온 방도 함께 지운다(soft delete). 카드는 그 대화의 결과물이라,
     * 카드만 지우고 대화를 남기면 사용자가 지웠다고 여긴 내용이 채팅방 목록·검색에 그대로 남는다.
     *
     * 이미 삭제된 방이면 넘어간다. 채팅방을 먼저 지운 뒤 카드를 지우는 순서에서도 카드 삭제는
     * 성공해야 한다.
     */
    @Transactional
    fun deleteForCardRemoval(conversationId: Long) {
        val conversation =
            conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        if (conversation.isDeleted()) {
            return
        }
        conversation.delete()
    }
}
