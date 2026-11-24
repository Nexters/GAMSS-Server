package com.nexters.gamss.quality.repository

import com.nexters.gamss.quality.domain.ResponseHumanVerdict
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ResponseHumanVerdictRepository : JpaRepository<ResponseHumanVerdict, Long> {
    fun findByMessageIdAndAdminEmail(
        messageId: Long,
        adminEmail: String,
    ): ResponseHumanVerdict?

    fun findByMessageIdIn(messageIds: Collection<Long>): List<ResponseHumanVerdict>

    fun countByAdminEmail(adminEmail: String): Long

    fun findByAdminEmail(adminEmail: String): List<ResponseHumanVerdict>

    @Modifying
    @Query("delete from ResponseHumanVerdict v where v.conversationId in :conversationIds")
    fun deleteByConversationIds(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): Int

    @Modifying
    @Query("delete from ResponseHumanVerdict v where v.memberId = :memberId")
    fun deleteByMember(
        @Param("memberId") memberId: Long,
    ): Int
}
