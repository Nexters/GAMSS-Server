package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.service.DeletedConversationCleaner
import com.nexters.gamss.member.service.WithdrawnMemberCleaner
import com.nexters.gamss.quality.repository.ConversationViewLogRepository
import com.nexters.gamss.quality.repository.ResponseAiVerdictRepository
import com.nexters.gamss.quality.repository.ResponseHumanVerdictRepository
import org.springframework.stereotype.Component
import java.time.Instant

/** 판정과 열람 기록은 대화 원문에서 나온 데이터라 대화 삭제와 회원 탈퇴 때 함께 지운다. */
@Component
class QualityDataCleaner(
    private val aiVerdictRepository: ResponseAiVerdictRepository,
    private val humanVerdictRepository: ResponseHumanVerdictRepository,
    private val viewLogRepository: ConversationViewLogRepository,
) : DeletedConversationCleaner,
    WithdrawnMemberCleaner {
    override fun clean(
        conversationIds: Collection<Long>,
        deletedAt: Instant,
    ) {
        aiVerdictRepository.deleteByConversationIds(conversationIds)
        humanVerdictRepository.deleteByConversationIds(conversationIds)
        viewLogRepository.deleteByConversationIds(conversationIds)
    }

    override fun clean(memberId: Long) {
        aiVerdictRepository.deleteByMember(memberId)
        humanVerdictRepository.deleteByMember(memberId)
        viewLogRepository.deleteByMember(memberId)
    }
}
