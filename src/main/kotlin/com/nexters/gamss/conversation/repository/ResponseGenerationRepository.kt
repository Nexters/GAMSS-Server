package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.ResponseGeneration
import org.springframework.data.jpa.repository.JpaRepository

interface ResponseGenerationRepository : JpaRepository<ResponseGeneration, Long> {
    fun findByTriggerMessageIdIn(triggerMessageIds: Collection<Long>): List<ResponseGeneration>

    fun existsByTriggerMessageId(triggerMessageId: Long): Boolean
}
