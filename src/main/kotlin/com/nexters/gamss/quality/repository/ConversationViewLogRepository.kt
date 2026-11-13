package com.nexters.gamss.quality.repository

import com.nexters.gamss.quality.domain.ConversationViewLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ConversationViewLogRepository : JpaRepository<ConversationViewLog, Long> {
    @Modifying
    @Query("delete from ConversationViewLog l where l.conversationId in :conversationIds")
    fun deleteByConversationIds(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): Int

    @Modifying
    @Query("delete from ConversationViewLog l where l.memberId = :memberId")
    fun deleteByMember(
        @Param("memberId") memberId: Long,
    ): Int
}
