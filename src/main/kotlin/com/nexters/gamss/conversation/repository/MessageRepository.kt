package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.Message
import org.springframework.data.jpa.repository.JpaRepository

interface MessageRepository : JpaRepository<Message, Long> {
    fun findAllByConversationIdOrderByIdAsc(conversationId: Long): List<Message>
}
