package com.nexters.gamss.quality.repository

import com.nexters.gamss.quality.domain.ResponseAiVerdict
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ResponseAiVerdictRepository : JpaRepository<ResponseAiVerdict, Long> {
    fun findByMessageIdIn(messageIds: Collection<Long>): List<ResponseAiVerdict>

    fun findByMessageIdInAndJudgeModelAndJudgePromptVersion(
        messageIds: Collection<Long>,
        judgeModel: String,
        judgePromptVersion: Int,
    ): List<ResponseAiVerdict>

    fun findByMessageCreatedAtGreaterThanEqualAndMessageCreatedAtLessThan(
        from: Instant,
        to: Instant,
    ): List<ResponseAiVerdict>

    @Modifying
    @Query("delete from ResponseAiVerdict v where v.conversationId in :conversationIds")
    fun deleteByConversationIds(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): Int

    @Modifying
    @Query("delete from ResponseAiVerdict v where v.memberId = :memberId")
    fun deleteByMember(
        @Param("memberId") memberId: Long,
    ): Int
}
